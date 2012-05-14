package com.maestrodev.lucee.plugins.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListHostedZonesResult;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecordSet;

public class AWSCloudWorker
{

    public static void awsDns( String accessKey, String secretKey, String hostname, String domain )
    {
        AmazonRoute53 route53 = null;
        try
        {
            AWSCredentials credentials = new BasicAWSCredentials( accessKey, secretKey );
            route53 = new AmazonRoute53Client( credentials );

            ListHostedZonesResult hostedZones = route53.listHostedZones();

            ResourceRecordSet set = new ResourceRecordSet( hostname, RRType.A );
            Change change = new Change().withAction( ChangeAction.CREATE ).withResourceRecordSet( set );
            ChangeBatch changeBatch = new ChangeBatch().withChanges( change );
            ChangeResourceRecordSetsRequest changeResourceRecordSetsRequest =
                new ChangeResourceRecordSetsRequest( domain, changeBatch );

            // route53.createHostedZone( new CreateHostedZoneRequest().withName( domain )
            // .withCallerReference(callerReference)
            route53.changeResourceRecordSets( changeResourceRecordSetsRequest );

            // .withHostedZoneConfig( new HostedZoneConfig().withComment( "my first Route 53 hosted zone!" ) ) );
        }
        finally
        {
            if ( route53 != null )
            {
                route53.shutdown();
            }
        }
    }
}
