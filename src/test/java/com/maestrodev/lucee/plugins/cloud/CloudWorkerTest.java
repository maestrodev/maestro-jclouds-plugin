package com.maestrodev.lucee.plugins.cloud;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import org.apache.commons.io.IOUtils;
import org.jclouds.domain.LoginCredentials;
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
     * Live Test for CloudWorker Provision in AWS
     */
    // @Test
    public void testProvisionAws()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();

        cloudWorker.setWorkitem( loadJson( "aws-provision" ) );

        Method method = cloudWorker.getClass().getMethod( "provision" );
        method.invoke( cloudWorker );

        assertNull( cloudWorker.getError(), cloudWorker.getError() );
    }

    /**
     * Live Test for CloudWorker Deprovision in AWS
     */
    // @Test
    public void testDeprovisionAws()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();
        cloudWorker.setWorkitem( loadJson( "aws-deprovision" ) );

        Method method = cloudWorker.getClass().getMethod( "deprovision" );
        method.invoke( cloudWorker );

        assertNull( cloudWorker.getError(), cloudWorker.getError() );
    }

    @Test
    @SuppressWarnings( "unchecked" )
    public void testProvisionWrongProvider()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();
        JSONObject json = loadJson( "aws-provision" );
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
        LoginCredentials credentials = cloudWorker.getLoginCredentials( "src/test/resources/test-key" );
        assertNotNull( credentials.getPrivateKey() );
    }

    private JSONObject loadJson( String name )
        throws IOException, ParseException
    {
        InputStream is = null;
        try
        {
            String f = "com/maestrodev/lucee/plugins/cloud/" + name + ".json";
            is = this.getClass().getClassLoader().getResourceAsStream( f );
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
