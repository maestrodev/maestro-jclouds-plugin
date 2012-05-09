package com.maestrodev.lucee.plugins.cloud;

import static com.google.common.base.Charsets.*;
import static com.google.common.collect.Iterables.*;
import static java.lang.String.*;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.*;
import static org.jclouds.compute.config.ComputeServiceProperties.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.ContextBuilder;
import org.jclouds.apis.ApiMetadata;
import org.jclouds.apis.Apis;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
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
import org.jclouds.sshj.config.SshjSshClientModule;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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

    public CloudWorker()
    {
        super();
    }

    /**
     * Cloud Provision Task
     **/
    public void provision()
    {
        logger.debug( "Starting provisioning" );

        String groupName = "Maestro";

        String provider = getField( "type" );
        String identity = getField( "key_id" );
        String credential = getField( "key" );
        String groups = getField( "groups" );
        // TODO this should be an array
        List<String> securityGroups = Arrays.asList( groups.split( " " ) );
        String domain = getField( "domain" );
        String keyName = getField( "key_name" );
        String sshUser = getField( "ssh_user" );
        String imageId = getField( "image_id" );
        String flavorId = getField( "flavor_id" );
        String availabilityZone = getField( "availability_zone" );

        // optional
        String privateKeyPath = getField( "private_key_path" );
        JSONArray sshCommands = (JSONArray) getFields().get( "ssh_commands" );
        String provisionCommand = getField( "provision_command" );
        String deprovisionCommand = getField( "deprovision_command" );
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
            String hostname = processHostname( compute );

            logger.debug( "adding node to group {}", groupName );

            // Default template chooses the smallest size on an operating system
            // that tested to work with java, which tends to be Ubuntu or CentOS
            TemplateBuilder templateBuilder = compute.templateBuilder();

            // note this will create a user with the same name as you on the
            // node. ex. you can connect via ssh publicip
            // Statement bootInstructions = AdminAccess.standard();

            // to run commands as root, we use the runScript option in the template.
            Template template = templateBuilder.imageId( imageId ).build();// .locationId( availabilityZone
                                                                           // ).hardwareId( flavorId ).options(
            TemplateOptions options = template.getOptions();

            // run scripts on startup
            if ( !StringUtils.isEmpty( privateKeyPath ) )
            {
                options.overrideLoginCredentials( getLoginCredentials( privateKeyPath ) );
            }
            options.runScript( bootstrap ).blockOnComplete( true );

            // name the instance
            options.getUserMetadata().put( "Name", hostname + "." + domain );

            if ( isAmazon( provider ) )
            {
                template.getOptions().as( EC2TemplateOptions.class ).securityGroups( securityGroups ).keyPair( keyName ).userData( userData.getBytes() );
            }

            NodeMetadata node = getOnlyElement( compute.createNodesInGroup( "default", 1, template ) );
            logger.info( "Started node {}: {}", node.getId(),
                         concat( node.getPrivateAddresses(), node.getPublicAddresses() ) );

            // setField( "ip", node.getPublicAddresses(). );
            setField( "instance_id", node.getProviderId() );
            setField( "instance_dns", "" );
            Set<String> privateAddresses = node.getPrivateAddresses();

            // write_output( "\nLaunched machine: <a href=\"http://#{m.name}\">#{m.name} - id: #{m.instance_id}</a>" );

            // Capture an array of machines so that we can know what to deprovision if necessary
            machinePush( node.getProviderId() );
            Iterator<String> it = node.getPublicAddresses().iterator();
            String publicAddress = it.hasNext() ? it.next() : node.getProviderId();
            setField( "body", "Provisioned machine at " + publicAddress );

        }
        catch ( Exception e )
        {
            logger.error( "Error provisioning", e );
            setError( "Error provisioning: " + e.getMessage() );
        }
        finally
        {
            if ( compute != null )
            {
                compute.getContext().close();
            }
        }
        logger.debug( "Done provisioning" );
    }

    /**
     * Cloud Deprovision task
     * 
     * @throws Exception
     */
    public void deprovision()
        throws Exception
    {
        logger.debug( "Starting deprovisioning" );

        String provider = getField( "type" );
        String identity = getField( "key_id" );
        String credential = getField( "key" );

        // required
        getField( "ssh_user" );
        getField( "key_name" );
        // optional
        getField( "ssh_commands" );

        String id = getField( "instance_id" );

        ComputeService compute = null;
        try
        {
            compute = initComputeService( provider, identity, credential );

            compute.destroyNode( id );

        }
        catch ( Exception e )
        {
            logger.error( "Error deprovisioning", e );
            setError( "Error deprovisioning: " + e.getMessage() );
        }
        finally
        {
            if ( compute != null )
            {
                compute.getContext().close();
            }
        }
        logger.debug( "Done deprovisioning" );
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

        // without this it won't find the AMI
        properties.setProperty( PROPERTY_EC2_CC_AMI_QUERY, "" );

        long scriptTimeout = TimeUnit.MILLISECONDS.convert( 20, TimeUnit.MINUTES );
        properties.setProperty( TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "" );

        // injecting a ssh implementation
        Iterable<Module> modules =
            ImmutableSet.<Module> of( new SshjSshClientModule(), new SLF4JLoggingModule(),
                                      new EnterpriseConfigurationModule() );

        logger.info( "Connecting to cloud {}", provider );

        ContextBuilder builder =
            ContextBuilder.newBuilder( provider ).credentials( identity, credential ).modules( modules ).overrides( properties );

        logger.debug( "Initializing cloud {}", builder.getApiMetadata() );

        return builder.buildView( ComputeServiceContext.class ).getComputeService();
    }

    private LoginCredentials getLoginForCommandExecution( String user, String privateKeyPath )
        throws IOException
    {
        String privateKey = Files.toString( new File( privateKeyPath ), UTF_8 );
        return LoginCredentials.builder().user( user ).privateKey( privateKey ).build();
    }

    private boolean isAmazon( String provider )
    {
        return "aws-ec2".equals( provider );
    }

    private String processHostname( ComputeService compute )
    {
        String hostname = getField( "hostname" );
        if ( StringUtils.isEmpty( hostname ) )
        {
            try
            {
                hostname = InetAddress.getLocalHost().getHostName();
            }
            catch ( UnknownHostException e )
            {
                hostname = "localhost";
            }
            hostname += "_" + compute.listNodes().size();
        }
        return hostname;
    }

    @SuppressWarnings( "unchecked" )
    private void machinePush( String instanceId )
    {
        JSONArray machines = (JSONArray) getFields().get( "machines" );
        if ( machines == null )
        {
            machines = new JSONArray();
        }
        machines.add( instanceId );
        getFields().put( "machines", machines );
    }

    protected LoginCredentials getLoginCredentials( String privateKeyPath )
        throws IOException
    {
        File file = new File( privateKeyPath );
        if ( !file.exists() )
        {
            File anotherFile = new File( System.getProperty( "user.home" ) + "/.ssh/" + privateKeyPath );
            if ( anotherFile.exists() )
            {
                file = anotherFile;
            }
        }
        String privateKeyString = FileUtils.readFileToString( file.getAbsoluteFile(), UTF_8.displayName() );
        return LoginCredentials.builder().privateKey( privateKeyString ).build();
    }
}
