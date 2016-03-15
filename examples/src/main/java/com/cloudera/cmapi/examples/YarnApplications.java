/**
 * Licensed to Cloudera, Inc. under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance  with the License.  
 * You may obtain a copy of the License at
 * 
 *    http:www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.cmapi.examples;

import com.cloudera.api.ClouderaManagerClientBuilder;
import com.cloudera.api.DataView;
import com.cloudera.api.model.ApiCluster;
import com.cloudera.api.model.ApiClusterList;
import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.model.ApiYarnApplication;
import com.cloudera.api.model.ApiYarnApplicationResponse;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v10.ServicesResourceV10;
import com.cloudera.api.v6.YarnApplicationsResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.joda.time.DateTime;

/**
 * mvn package
 *  mvn exec:java -Dcmapi.properties.file=cmexamples.properties \
 *  -Dexec.mainClass="com.cloudera.cmapi.examples.YarnApplications"
 * or
 * java -cp target/examples-1.0-SNAPSHOT-executable.jar \
 * -Dcmapi.properties.file=cmexamples.properties \
 * com.cloudera.cmapi.examples.YarnApplications
 */
public class YarnApplications {

  public static void main(String[] args) {

    String propfile = System.getProperty("cmapi.properties.file");
    Properties cmprops = new Properties();
    try {
      YarnApplications ya = new YarnApplications();
      cmprops = ya.getProperties(propfile);
    } catch(IOException e) {
      e.printStackTrace();
    }

    // Get API root:
    RootResourceV10 apiRoot = new ClouderaManagerClientBuilder()
      .withHost((String)cmprops.get("cmhost"))
      .withPort(7180)
      .withUsernamePassword((String)cmprops.get("cmuser"), (String)
                            cmprops.get("cmpass"))
      .build()
      .getRootV10();

    // Get a list of defined clusters.
    // '/api/v10/clusters'
    ApiClusterList clusters = apiRoot.getClustersResource()
      .readClusters(DataView.SUMMARY);

    // We'll just use the first cluster in the returned list.
    ApiCluster cluster = clusters.get(0);
    System.out.println("Cluster name=" + cluster.getName());
      
    // Get a list of services for the cluster and use it to find the YARN
    // service name.
    // '/api/v10/clusters/HANA-benchmark/services'
    String yarnServiceName = null;
    ServicesResourceV10 servicesResource = 
      apiRoot.getClustersResource().getServicesResource(cluster.getName());

    for (ApiService service : servicesResource.readServices(DataView.FULL)) {
      if ("YARN".equals(service.getType())) {
        System.out.println(service.getName());
        yarnServiceName = service.getName();
        break;
      }
    }

    // Then use the YARN service name to get the resource object we'll use
    // to make requests for application info:
    YarnApplicationsResource resource =
      servicesResource.getYarnApplicationsResource(yarnServiceName);

    // Display up to 10 applications for default of last 5 minutes to now.
    // '/api/v10/clusters/CLUSTER_NAME/services/YARN_SERVICE_NAME/yarnApplications'
    ApiYarnApplicationResponse response = 
      resource.getYarnApplications(yarnServiceName,
                                   null, null, null, 10, 0);
    showApplicationInfo(response);

    // Display up to 10 applications from 1 hour ago to now.
    response = 
      resource.getYarnApplications(yarnServiceName,
                                   null, 
                                   new DateTime().minusHours(1).toString(), null, 
                                   10, 0);
    showApplicationInfo(response);

    // Display applications from 24 hours ago to 6 hours ago.
    response = 
      resource.getYarnApplications(yarnServiceName,
                                   null, 
                                   new DateTime().minusDays(1).toString(),
                                   new DateTime().minusHours(6).toString(), 
                                   10, 0);
    showApplicationInfo(response);

    // Display currently executing applications only.
    response = 
      resource.getYarnApplications(yarnServiceName,
                                   "executing=true", null, null, 10, 0);
    showApplicationInfo(response);
  }

  private static void showApplicationInfo(ApiYarnApplicationResponse response) {
    List<ApiYarnApplication> applications = response.getApplications();
    System.out.println("Displaying " + applications.size() + " applications:");
    for (ApiYarnApplication application : applications) {
      System.out.println("\t ID = " + application.getApplicationId());
      System.out.println("\t name = " + application.getName());
      System.out.println("\t state = " + application.getState());
      System.out.println("\t start time = " + application.getStartTime());
      System.out.println("\t end time = " + application.getEndTime());
      System.out.println("\t progress = " + application.getProgress());
      System.out.println("\t user = " + application.getUser());
      System.out.println("\t pool = " + application.getPool());
      System.out.println("\t allocatedMB = " + application.getAllocatedMB());
      //System.out.println("\t allocatedVCores = " + getAllocatedVCores());
      //System.out.println("\t running containers = " + getRunningContainers());
      System.out.println("\t attributes:");
      Map<String, String> attrs = application.getAttributes();
      for (Map.Entry<String, String> entry : attrs.entrySet()) {
        System.out.println("\t " + entry.getKey() + " = " + entry.getValue());
      }
    }
  }

  public Properties getProperties(String propfile) 
    throws IOException {

    InputStream in = null;
    Properties props = new Properties();

    try {
      in = getClass().getClassLoader().getResourceAsStream(propfile);
      
      if (in != null) {
        props.load(in);
      } else {
        throw new FileNotFoundException(propfile + " not found");
      }
    } finally {
      in.close();
    }
       
    return props;
  }
}