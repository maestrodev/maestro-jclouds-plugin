package com.maestrodev.lucee.plugins.cloud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.maestrodev.MaestroWorker;

public class CloudWorker extends MaestroWorker {

    public CloudWorker() {
        super();
    }

    /**
    * EC2 Provision Task
    **/
    public void ec2Provision() throws Exception {
        try{
           
            
        } catch (Exception e){
            setError("Error Posting Message " + e.getMessage());
        }
    }

}