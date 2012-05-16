package com.maestrodev.lucee.plugins.cloud;

import static com.google.common.collect.Iterables.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.jclouds.domain.LoginCredentials;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

/**
 * Tests for Maestro Cloud plugin.
 */
public class CloudWorkerTest
{
    private static final JSONParser parser = new JSONParser();

    /**
     * Live Test for CloudWorker Provision and Deprovision in AWS
     */
    // @Test
    public void testAws()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();

        cloudWorker.setWorkitem( loadJson( "aws-provision" ) );

        cloudWorker.provision();

        assertNull( cloudWorker.getError(), cloudWorker.getError() );

        assertTrue( cloudWorker.getField( "body" ).matches( "Provisioned machine at .*" ) );
        assertFalse( cloudWorker.getField( "ip" ), StringUtils.isEmpty( cloudWorker.getField( "ip" ) ) );
        assertFalse( cloudWorker.getField( "instance_id" ), StringUtils.isEmpty( cloudWorker.getField( "instance_id" ) ) );
        assertFalse( cloudWorker.getField( "instance_dns" ),
                     StringUtils.isEmpty( cloudWorker.getField( "instance_dns" ) ) );
        JSONArray machines = (JSONArray) cloudWorker.getFields().get( "machines" );
        assertEquals( 1, machines.size() );
        String machine = (String) machines.get( 0 );
        assertTrue( machine, machine.matches( "us-east-1/i-[.]{8}" ) );

        cloudWorker.deprovision();

        assertNull( cloudWorker.getError(), cloudWorker.getError() );
    }

    /**
     * Test for CloudWorker Provision and Deprovision with stub provider
     */
    @Test
    public void testStub()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();

        cloudWorker.setWorkitem( loadJson( "stub-provision" ) );

        // provision
        cloudWorker.provision();

        assertNull( cloudWorker.getError(), cloudWorker.getError() );

        assertTrue( cloudWorker.getField( "body" ).matches( "Provisioned machine at .*" ) );
        assertFalse( cloudWorker.getField( "ip" ), StringUtils.isEmpty( cloudWorker.getField( "ip" ) ) );
        assertFalse( cloudWorker.getField( "instance_id" ), StringUtils.isEmpty( cloudWorker.getField( "instance_id" ) ) );
        assertFalse( cloudWorker.getField( "instance_dns" ),
                     StringUtils.isEmpty( cloudWorker.getField( "instance_dns" ) ) );
        List<String> machines = cloudWorker.getArrayField( String.class, "machines" );
        assertEquals( 1, machines.size() );
        String machine = (String) machines.get( 0 );
        assertEquals( "1", machine );

        // compare stub data
        ComputeService compute = cloudWorker.getComputeService();
        Set<? extends ComputeMetadata> nodes = compute.listNodes();
        assertEquals( 1, nodes.size() );
        ComputeMetadata node = getOnlyElement( nodes );
        NodeMetadata metadata = compute.getNodeMetadata( node.getId() );
        assertEquals( NodeState.RUNNING, metadata.getState() );

        // deprovision
        cloudWorker.deprovision();
        assertNull( cloudWorker.getError(), cloudWorker.getError() );

        // wait a bit until nodes are destroyed
        for ( int i = 0; i < 100; i++ )
        {
            nodes = compute.listNodes();
            if ( nodes.size() > 0 )
            {
                node = getOnlyElement( nodes );
                metadata = compute.getNodeMetadata( node.getId() );
                // node may get deleted between listNodes() and getNodeMetadata()
                if ( metadata != null )
                {
                    assertEquals( NodeState.TERMINATED, metadata.getState() );
                    // provider takes some time to destroy the nodes
                    // see StubComputeServiceAdapter.destroyNodes
                    Thread.sleep( 2000 );
                }
            }
            else
            {
                break;
            }
        }
        nodes = compute.listNodes();
        assertEquals( 0, nodes.size() );
    }

    @Test
    @SuppressWarnings( "unchecked" )
    public void testProvisionWrongProvider()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();
        JSONObject json = loadJson( "stub-provision" );
        ( (JSONObject) json.get( "fields" ) ).put( "type", "aws" );
        cloudWorker.setWorkitem( json );

        Method method = cloudWorker.getClass().getMethod( "provision" );
        method.invoke( cloudWorker );

        assertTrue( cloudWorker.getError(), cloudWorker.getError().matches( "Provider aws not in supported list.*" ) );
    }

    @Test
    public void testGetLoginCredentials()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();
        LoginCredentials credentials = cloudWorker.getLoginCredentials( "user", "src/test/resources/test-key" );
        assertNotNull( credentials.getPrivateKey() );
        assertEquals( "user", credentials.getUser() );

        credentials = cloudWorker.getLoginCredentials( null, "src/test/resources/test-key" );
        assertNotNull( credentials.getPrivateKey() );
        assertEquals( "root", credentials.getUser() );

        credentials = cloudWorker.getLoginCredentials( "", "src/test/resources/test-key" );
        assertNotNull( credentials.getPrivateKey() );
        assertEquals( "root", credentials.getUser() );
    }

    public static JSONObject loadJson( String name )
        throws IOException, ParseException
    {
        InputStream is = null;
        try
        {
            String f = "com/maestrodev/lucee/plugins/cloud/" + name + ".json";
            is = CloudWorkerTest.class.getClassLoader().getResourceAsStream( f );
            if ( is == null )
            {
                throw new IllegalStateException( "File not found " + f );
            }
            else
            {
                JSONObject json = (JSONObject) parser.parse( IOUtils.toString( is ) );
                return json;
            }
        }
        finally
        {
            IOUtils.closeQuietly( is );
        }
    }

}
