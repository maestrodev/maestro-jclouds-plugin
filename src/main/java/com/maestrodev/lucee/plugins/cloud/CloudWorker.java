package com.maestrodev.lucee.plugins.cloud;

import com.maestrodev.MaestroWorker;

public class CloudWorker
    extends MaestroWorker
{

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
        try
        {

        }
        catch ( Exception e )
        {
            setError( "Error Posting Message " + e.getMessage() );
        }
    }

    /**
     * Cloud Deprovision task
     * 
     * @throws Exception
     */
    public void deprovision()
        throws Exception
    {
        try
        {

        }
        catch ( Exception e )
        {
            setError( "Error Posting Message " + e.getMessage() );
        }
    }
}
