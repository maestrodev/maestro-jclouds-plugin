package com.maestrodev.lucee.plugins.cloud;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.json.simple.JSONObject;
import org.junit.Test;

/**
 * Tests for Maestro Cloud plugin.
 */
public class CloudWorkerTest
{
    /**
     * Test CloudWorker Provision
     */
    @SuppressWarnings( "unchecked" )
    @Test
    public void testProvision()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();
        JSONObject fields = new JSONObject();
        // required
        fields.put( "key_id", "yyy" );
        fields.put( "key", "xxx" );
        fields.put( "ssh_user", "xxx" );
        fields.put( "image_id", "image_id" );
        fields.put( "domain", "xxx" );
        fields.put( "type", "xxx" );
        fields.put( "flavor_id", "t1.micro" );
        fields.put( "groups", "g" );
        fields.put( "key_name", "g" );
        fields.put( "availability_zone", "g" );

        // optional
        fields.put( "ssh_commands", new String[] {} );
        fields.put( "hostname", "xxx" );
        fields.put( "private_key_path", "g" );
        fields.put( "provision_command", "path" );
        fields.put( "deprovision_command", "path" );
        fields.put( "bootstrap", "path" );
        fields.put( "user_data", "path" );

        JSONObject workitem = new JSONObject();
        workitem.put( "fields", fields );
        cloudWorker.setWorkitem( workitem );

        Method method = cloudWorker.getClass().getMethod( "provision" );
        method.invoke( cloudWorker );

        //assertNull( cloudWorker.getError(), cloudWorker.getError() );
    }

    /**
     * Test CloudWorker Deprovision
     */
    @Test
    @SuppressWarnings( "unchecked" )
    public void testDeprovision()
        throws Exception
    {
        CloudWorker cloudWorker = new CloudWorker();
        JSONObject fields = new JSONObject();
        // required
        fields.put( "key_id", "yyy" );
        fields.put( "key", "xxx" );
        fields.put( "ssh_user", "xxx" );
        fields.put( "key_name", "g" );
        // optional
        fields.put( "ssh_commands", new String[] {} );

        JSONObject workitem = new JSONObject();
        workitem.put( "fields", fields );
        cloudWorker.setWorkitem( workitem );

        Method method = cloudWorker.getClass().getMethod( "deprovision" );
        method.invoke( cloudWorker );

        //assertNull( cloudWorker.getError(), cloudWorker.getError() );
    }

}
