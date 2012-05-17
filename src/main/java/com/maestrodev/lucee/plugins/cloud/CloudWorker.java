package com.maestrodev.lucee.plugins.cloud;

import static com.google.common.base.Charsets.*;
import static com.google.common.base.Predicates.*;
import static com.google.common.collect.Iterables.*;
import static java.lang.String.*;
import static org.apache.commons.lang3.StringUtils.*;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.*;
import static org.jclouds.compute.config.ComputeServiceProperties.*;
import static org.jclouds.compute.options.TemplateOptions.Builder.*;
import static org.jclouds.compute.predicates.NodePredicates.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jclouds.ContextBuilder;
import org.jclouds.apis.ApiMetadata;
import org.jclouds.apis.Apis;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.providers.ProviderMetadata;
import org.jclouds.providers.Providers;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.Module;
import com.maestrodev.MaestroWorker;

public class CloudWorker
    extends MaestroWorker
{

    private final Logger logger = LoggerFactory.getLogger( this.getClass() );

    private static final Map<String, ApiMetadata> allApis =
        Maps.uniqueIndex( Apis.viewableAs( ComputeServiceContext.class ), Apis.idFunction() );

    private static final Map<String, ProviderMetadata> appProviders =
        Maps.uniqueIndex( Providers.viewableAs( ComputeServiceContext.class ), Providers.idFunction() );

    private static final Set<String> allKeys = ImmutableSet.copyOf( Iterables.concat( appProviders.keySet(),
                                                                                      allApis.keySet() ) );

    private static final String JCLOUDS_GROUP_NAME = "maestro";

    private ComputeService computeService;

    protected ComputeService getComputeService()
    {
        return computeService;
    }

    /**
     * Cloud Provision Task
     * 
     * @throws RunNodesException
     * @throws RunScriptOnNodesException
     **/
    public void provision()
        throws RunNodesException, RunScriptOnNodesException
    {
        String msg = "Starting provisioning\n";
        logger.debug( msg );
        writeOutput( msg );

        String provider = getField( "type" );
        String identity = getField( "key_id" );
        String credential = getField( "key" );
        String groups = getField( "groups" );
        // TODO this should be an array
        List<String> securityGroups = Arrays.asList( groups.split( " " ) );
        String domain = getField( "domain" );
        String keyName = getField( "key_name" );
        String imageId = getField( "image_id" );
        String flavorId = getField( "flavor_id" );
        String availabilityZone = getField( "availability_zone" );

        // optional
        List<String> sshCommands = getArrayField( String.class, "ssh_commands" );
        String provisionCommand = getField( "provision_command" );
        String bootstrap = getField( "bootstrap" );
        String userData = getField( "user_data" );

        // check if a provider is present ahead of time
        if ( !contains( allKeys, provider ) )
        {
            logger.error( "Provider {} not in supported list: {}", provider, allKeys );
            setError( format( "Provider %s not in supported list: %s", provider, allKeys ) );
            return;
        }

        /* EC2 image ids must contain the region */
        if ( isAmazon( provider ) )
        {
            String region = availabilityZone.substring( 0, availabilityZone.length() - 1 );
            imageId = region + "/" + imageId;
        }

        ComputeService compute = null;
        try
        {
            compute = initComputeService( provider, identity, credential );
            String hostname = processHostname();

            logger.debug( "adding node to group {}", JCLOUDS_GROUP_NAME );

            TemplateBuilder templateBuilder = compute.templateBuilder();

            templateBuilder.imageId( imageId ).hardwareId( flavorId );
            if ( !isEmpty( availabilityZone ) )
            {
                templateBuilder.locationId( availabilityZone );
            }
            Template template = templateBuilder.build();
            TemplateOptions options = template.getOptions();

            // credentials to run scripts
            LoginCredentials loginCredentials = getLoginCredentials();
            options.overrideLoginCredentials( loginCredentials );

            // to run commands as root, we use the runScript option in the template.
            if ( !isEmpty( bootstrap ) )
            {
                options.runScript( bootstrap ).blockOnComplete( true );
            }

            // name the instance with the Name tag
            String nameTag = hostname + ( isEmpty( domain ) ? "" : "." + domain );
            options.getUserMetadata().put( "Name", nameTag );

            // Amazon specific options
            if ( isAmazon( provider ) )
            {
                EC2TemplateOptions ec2Options = template.getOptions().as( EC2TemplateOptions.class );
                ec2Options.securityGroups( securityGroups ).keyPair( keyName );
                if ( !isEmpty( userData ) )
                {
                    ec2Options.userData( userData.getBytes() );
                }
            }

            // start the nodes
            NodeMetadata node = getOnlyElement( compute.createNodesInGroup( JCLOUDS_GROUP_NAME, 1, template ) );
            // NodeMetadata node =
            // getOnlyElement( filter( compute.listNodesDetailsMatching( all() ),
            // and( inGroup( JCLOUDS_GROUP_NAME ), not( TERMINATED ) ) ) );

            msg = format( "Started node %s: %s%n", node.getId(), node.getPublicAddresses() );
            logger.info( msg );
            writeOutput( msg );

            logger.debug( "Node: {}", node );

            // get data from running nodes
            String publicAddress = getOnlyElement( node.getPublicAddresses() );
            setField( "ip", publicAddress );
            setField( "instance_id", node.getProviderId() );
            setField( "instance_dns", publicAddress );

            msg =
                format( "Launched machine: <a href=\"http://%s\">%s - id: %s</a>%n", publicAddress, publicAddress,
                        node.getProviderId() );
            logger.debug( msg );
            writeOutput( msg );

            // execute the ssh and provision commands
            executeScripts( compute, loginCredentials,
                            Predicates.<NodeMetadata> and( inGroup( JCLOUDS_GROUP_NAME ), withIds( node.getId() ) ),
                            sshCommands, provisionCommand );

            // Capture an array of machines so that we can know what to deprovision if necessary
            machinePush( node.getId() );

            setField( "body", format( "Provisioned machine at %s", publicAddress ) );

        }
        catch ( AuthorizationException e )
        {
            logger.error( format( "Error provisioning: authorization error for key id %s: %s", identity, e.getMessage() ) );
            setError( format( "Error provisioning: authorization error for key id %s: %s%n", identity, e.getMessage() ) );
        }
        finally
        {
            if ( compute != null )
            {
                compute.getContext().close();
            }
        }

        msg = "Done provisioning\n";
        logger.debug( msg );
        writeOutput( msg );
    }

    /**
     * Cloud Deprovision task
     * 
     * @throws Exception
     */
    public void deprovision()
        throws Exception
    {
        String msg = "Starting deprovisioning\n";
        logger.info( msg );
        writeOutput( msg );

        String provider = getField( "type" );
        String identity = getField( "key_id" );
        String credential = getField( "key" );

        List<String> sshCommands = getArrayField( String.class, "ssh_commands" );

        List<String> machines = getArrayField( String.class, "machines" );
        String[] ids = (String[]) machines.toArray( new String[0] );

        ComputeService compute = null;
        try
        {
            compute = initComputeService( provider, identity, credential );

            // execute the ssh deprovision commands
            if ( ( sshCommands != null ) && !sshCommands.isEmpty() )
            {
                // credentials to run scripts
                LoginCredentials loginCredentials = getLoginCredentials();

                executeScripts( compute, loginCredentials,
                                Predicates.<NodeMetadata> and( withIds( ids ), not( TERMINATED ) ), sshCommands, null );
            }

            Set<? extends NodeMetadata> nodes =
                compute.destroyNodesMatching( Predicates.<NodeMetadata> and( withIds( ids ),
                                                                             inGroup( JCLOUDS_GROUP_NAME ) ) );

            if ( nodes.isEmpty() || ( nodes.size() != machines.size() ) )
            {
                msg = format( "Not all machines were deprovisioned, tried: %s. Deprovisioned: %s%n", machines, nodes );
                setError( msg );
            }
            else
            {
                msg = format( "Deprovisioned machines: %s%n", nodes );
            }
            logger.debug( msg );
            writeOutput( msg );

        }
        catch ( AuthorizationException e )
        {
            logger.error( format( "Error deprovisioning: authorization error for key id %s", identity ), e );
            setError( format( "Error deprovisioning: authorization error for key id %s: %s", identity, e.getMessage() ) );
        }
        finally
        {
            if ( compute != null )
            {
                compute.getContext().close();
            }
        }
        msg = "Done deprovisioning\n";
        logger.debug( msg );
        writeOutput( msg );
    }

    /**
     * Get the jClouds compute service API to run all our operations
     * 
     * @param provider supported provider from http://www.jclouds.org/documentation/reference/supported-providers, ie.
     *            aws-ec2
     * @param identity
     * @param credential
     * @return
     */
    private ComputeService initComputeService( String provider, String identity, String credential )
    {
        // example of specific properties, in this case optimizing image list to
        // only amazon supplied
        Properties properties = new Properties();

        // don't prefetch Alestic, Canonical, RightScale images
        properties.setProperty( PROPERTY_EC2_AMI_QUERY, "" );

        // don't connect to all regions
        // properties.setProperty( PROPERTY_REGIONS, region );
        properties.setProperty( PROPERTY_EC2_CC_REGIONS, "" );

        // without this it won't find the AMI
        properties.setProperty( PROPERTY_EC2_CC_AMI_QUERY, "" );

        long scriptTimeout = TimeUnit.MILLISECONDS.convert( 20, TimeUnit.MINUTES );
        properties.setProperty( TIMEOUT_SCRIPT_COMPLETE, String.valueOf( scriptTimeout ) );

        // injecting logging and ssh implementation
        List<Module> modules =
            Lists.<Module> newArrayList( new SLF4JLoggingModule(), new EnterpriseConfigurationModule() );
        // stub provider will fail with a ssh implementation
        // see https://groups.google.com/forum/?hl=en&fromgroups#!topic/jclouds/USdRVB0IZ3U
        if ( !isStub( provider ) )
        {
            modules.add( new SshjSshClientModule() );
        }

        logger.info( "Connecting to cloud {}", provider );

        ContextBuilder builder =
            ContextBuilder.newBuilder( provider ).credentials( identity, credential ).modules( modules ).overrides( properties );

        logger.debug( "Initializing cloud {}", builder.getApiMetadata() );

        computeService = builder.buildView( ComputeServiceContext.class ).getComputeService();
        return computeService;
    }

    private boolean isAmazon( String provider )
    {
        return "aws-ec2".equals( provider );
    }

    private boolean isStub( String provider )
    {
        return "stub".equals( provider );
    }

    private String processHostname()
    {
        String hostname = getField( "hostname" );
        if ( isEmpty( hostname ) )
        {
            try
            {
                hostname = InetAddress.getLocalHost().getHostName();
            }
            catch ( UnknownHostException e )
            {
                hostname = "localhost";
            }
        }
        return hostname;
    }

    private void executeScripts( ComputeService compute, LoginCredentials loginCredentials,
                                 Predicate<NodeMetadata> predicate, List<String> sshCommands, String provisionCommand )
        throws RunScriptOnNodesException
    {
        // execute commands after instance is up
        List<String> commands;
        if ( sshCommands == null )
        {
            commands = Collections.emptyList();
        }
        else
        {
            commands = Lists.newArrayList( sshCommands );
        }

        List<Statement> statements = Lists.newArrayList();
        for ( String command : commands )
        {
            statements.add( Statements.exec( command ) );
        }
        if ( !isEmpty( provisionCommand ) )
        {
            statements.add( Statements.exec( provisionCommand ) );
        }

        // when you run commands, you can pass options to decide whether to
        // run it as root, supply or own credentials vs from cache, and wrap
        // in an init script vs directly invoke
        if ( !statements.isEmpty() )
        {
            Map<? extends NodeMetadata, ExecResponse> responses =
                compute.runScriptOnNodesMatching( predicate,
                                                  Statements.newStatementList( statements.toArray( new Statement[0] ) ),
                                                  overrideLoginCredentials( loginCredentials ) );

            for ( Entry<? extends NodeMetadata, ExecResponse> entry : responses.entrySet() )
            {
                NodeMetadata node = entry.getKey();
                ExecResponse response = entry.getValue();
                String msg =
                    format( "SSH commands in node %s finished with status %d:%n%s%n", node.getId(),
                            response.getExitStatus(), response.getOutput() );
                logger.debug( msg );
                writeOutput( msg );
            }
        }
    }

    @SuppressWarnings( "unchecked" )
    private void machinePush( String instanceId )
    {
        List<String> machines = getArrayField( String.class, "machines" );
        if ( machines == null )
        {
            machines = Lists.newArrayList();
        }
        machines.add( instanceId );
        getFields().put( "machines", machines );
    }

    protected LoginCredentials getLoginCredentials()
    {
        String sshUser = getField( "ssh_user" );
        String privateKeyPath = getField( "private_key_path" );
        return getLoginCredentials( sshUser, privateKeyPath );
    }

    protected LoginCredentials getLoginCredentials( String user, String privateKeyPath )
    {
        user = isEmpty( user ) ? "root" : user;

        if ( isEmpty( privateKeyPath ) )
        {
            privateKeyPath = System.getProperty( "user.home" ) + "/.ssh/id_rsa";
        }

        File file = new File( privateKeyPath );
        if ( !file.exists() )
        {
            File anotherFile = new File( System.getProperty( "user.home" ) + "/.ssh/" + privateKeyPath );
            if ( anotherFile.exists() )
            {
                file = anotherFile;
            }
        }
        String privateKeyString;

        try
        {
            privateKeyString = Files.toString( file.getAbsoluteFile(), UTF_8 );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( format( "Error reading file %s: %s", file.getAbsoluteFile(), e.getMessage() ),
                                        e );
        }

        return LoginCredentials.builder().user( user ).privateKey( privateKeyString ).build();
    }
}
