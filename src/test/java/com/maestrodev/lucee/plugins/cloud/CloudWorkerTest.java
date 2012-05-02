package com.maestrodev.lucee.plugins.cloud;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.json.simple.JSONObject;

import com.maestrodev.lucee.plugins.cloud.CloudWorker;

/**
 * Unit test for plugin.
 */
public class CloudWorkerTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public CloudWorkerTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( CloudWorkerTest.class );
    }
    
    /**x
     * Test CloudWorker
     */
    public void testEc2Provision() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        CloudWorker cloudWorker = new CloudWorker();
        JSONObject fields = new JSONObject();
        fields.put("secretkey", "xxx");
        fields.put("key_id", "yyy");        
        fields.put("keypair", "keypair");
        fields.put("image_id", "image_id");
        fields.put("security_group", "security_group");
        fields.put("flavor_id", "t1.micro");
        
        JSONObject workitem = new JSONObject();
        workitem.put("fields", fields);
        cloudWorker.setWorkitem(workitem);
               
        Method method = cloudWorker.getClass().getMethod("ec2Provision");
        method.invoke(cloudWorker);
    }
    
}
