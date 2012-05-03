package com.maestrodev.lucee.plugins.cloud;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.ssh.jsch.config.JschSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.maestrodev.MaestroWorker;

public class CloudWorker
    extends MaestroWorker
{

    private final Logger logger = LoggerFactory.getLogger( this.getClass() );

    public CloudWorker()
    {
        super();
    }

    /**
     * Cloud Provision Task
     **/
    public void provision()
        throws Exception
    {
        logger.debug( "Starting provisioning" );

        ComputeServiceContext context = null;
        try
        {
            context = getContext( getField( "type" ), getField( "key_id" ), getField( "key" ) );

            // TODO this should be an array
            String groups = getField( "groups" );
            List<String> securityGroups = Arrays.asList( groups.split( " " ) );

            provisionNodes( context, getField( "image_id" ), getField( "availability_zone" ), getField( "flavor_id" ),
                            getField( "key_name" ), securityGroups, getField( "user_data" ) );

            // TODO set fields: instance_dns, type,....

            // TODO
            getField( "ssh_user" );
            getField( "domain" );

            // optional
            getField( "ssh_commands" );
            getField( "hostname" );
            getField( "private_key_path" );
            getField( "provision_command" );
            getField( "deprovision_command" );
            getField( "bootstrap" );

        }
        catch ( Exception e )
        {
            logger.error( "Error provisioning", e );
            setError( "Error provisioning " + e.getMessage() );
        }
        finally
        {
            if ( context != null )
            {
                context.close();
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

        ComputeServiceContext context = null;
        try
        {
            context = getContext( getField( "type" ), getField( "key_id" ), getField( "key" ) );
            deprovisionNodes( context, getField( "instance_id" ) );

            // required
            getField( "ssh_user" );
            getField( "key_name" );
            // optional
            getField( "ssh_commands" );
        }
        catch ( Exception e )
        {
            logger.error( "Error deprovisioning", e );
            setError( "Error deprovisioning " + e.getMessage() );
        }
        finally
        {
            if ( context != null )
            {
                context.close();
            }
        }
        logger.debug( "Done deprovisioning" );
    }

    /**
     * Get the jClouds compute service API to run all our operations
     * 
     * @param provider supported provider from http://www.jclouds.org/documentation/reference/supported-providers, ie.
     *            aws-ec2
     * @param user
     * @param password
     * @return
     */
    private ComputeServiceContext getContext( String provider, String user, String password )
    {
        // ContextBuilder contextBuilder = ContextBuilder.newBuilder( provider );
        // RestContext context = contextBuilder.credentials( user, password ).build();

        Properties overrides = new Properties();
        Set<Module> wiring =
            ImmutableSet.<Module> of( new JschSshClientModule(), new SLF4JLoggingModule(),
                                      new EnterpriseConfigurationModule() );

        logger.info( "Connecting to cloud {}", provider );
        ComputeServiceContext context =
            new ComputeServiceContextFactory().createContext( provider, user, password, wiring, overrides );

        return context;
    }

    /**
     * Launch a server in the cloud
     * 
     * @return
     * @throws RunNodesException
     */
    private Set<? extends NodeMetadata> provisionNodes( ComputeServiceContext context, String imageId,
                                                        String locationId, String hardwareId, String keyName,
                                                        List<String> securityGroups, String userData )
        throws RunNodesException
    {

        // pick the image id template
        Template template =
            context.getComputeService().templateBuilder().imageId( imageId ).locationId( locationId ).hardwareId( hardwareId ).build();

        // EC2 specific config for security groups and keys
        template.getOptions().as( AWSEC2TemplateOptions.class ).securityGroups( securityGroups ).keyPair( keyName ).userData( userData.getBytes() );

        // run node accessible via group
        Set<? extends NodeMetadata> nodes = context.getComputeService().createNodesInGroup( "webserver", 1, template );

        return nodes;

        // }
        // catch ( NoSuchElementException e )
        // {
        // logger.error( "Unable to find Maestro image in cloud matching {}", imageId );
        // return nodes;
        // }

        // for ( NodeMetadata instance : nodes )
        // {
        // Image image = imageService.findOrCreateByImageId( instance.getImageId() );
        // server.setInstanceId( instance.getProviderId() );
        // Set<String> privateAddresses = instance.getPrivateAddresses();
        // if ( ( privateAddresses != null ) && ( !privateAddresses.isEmpty() ) )
        // {
        // server.setHost( privateAddresses.iterator().next() );
        // }
        // server.setImage( image );
        // server.setPlatform( platform.toString() );
        // server.setType( ServerType.EC2 );
        // server.setCreatedAt( Calendar.getInstance() );
        // if ( StringUtils.isEmpty( image.getName() ) )
        // {
        // template = templateBuilder.imageId( instance.getImageId() ).build();
        // image.setName( template.getImage().getDescription() );
        // image.setUpdatedAt( Calendar.getInstance() );
        // imageService.updateImage( image );
        // }
        // }

    }

    private void deprovisionNodes( ComputeServiceContext context, String id )
        throws RunNodesException
    {
        // destroy node using id
        context.getComputeService().destroyNode( id );
    }
}
