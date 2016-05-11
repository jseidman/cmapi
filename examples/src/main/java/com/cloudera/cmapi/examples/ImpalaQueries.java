/**
 * Licensed to Cloudera, Inc. under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance  with the License.
 * You may obtain a copy of the License a
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
import com.cloudera.api.model.ApiImpalaQuery;
import com.cloudera.api.model.ApiImpalaQueryResponse;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v10.ServicesResourceV10;
import com.cloudera.api.v6.ImpalaQueriesResourceV6;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Properties;import org.joda.time.DateTime;

/**
 * Example of using the Cloudera Manager API to show information on Impala
 * queries.
 *
 * To use:
 * <p><ul>
 * <li> Update cmhost, cmuser, and cmpass, cluster, and Impala service name
 * properties in src/main/resources/cmexamples.properties.
 * <li> Build: mvn package
 * <li> Execute: mvn exec:java -Dcmapi.properties.file=cmexamples.properties  -Dexec.mainClass="com.cloudera.cmapi.examples.ImpalaQueries"
 * <li> or java -cp target/examples-1.0-SNAPSHOT-executable.jar -Dcmapi.properties.file=cmexamples.properties com.cloudera.cmapi.examples.ImpalaQueries"
 * </ul><p>
 */
public class ImpalaQueries {

  /**
   * Execute the steps to call the API to get info on Impala queries.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) {

    String propfile = System.getProperty("cmapi.properties.file");
    Properties cmprops = new Properties();
    try {
      ImpalaQueries iq = new ImpalaQueries();
      cmprops = iq.getProperties(propfile);
    } catch(IOException e) {
      e.printStackTrace();
    }

    // Get API root:
    RootResourceV10 apiRoot = new ClouderaManagerClientBuilder()
      .withHost((String) cmprops.get("cmhost"))
      .withPort(7180)
      .withUsernamePassword((String) cmprops.get("cmuser"), 
                            (String) cmprops.get("cmpass"))
      .build()
      .getRootV10();

    String clusterName = (String) cmprops.get("cluster_name");
    ServicesResourceV10 servicesResource =
      apiRoot.getClustersResource().getServicesResource(clusterName);
    String impalaServiceName = (String) cmprops.get("impala_service_name");
    ImpalaQueriesResourceV6 queriesResource =
      servicesResource.getImpalaQueriesResource(impalaServiceName);
    ApiImpalaQueryResponse response = 
      queriesResource.getImpalaQueries(impalaServiceName,
                                       null,
                                       new DateTime().minusDays(1).toString(),
                                       null,
                                       1000,
                                       0);
    List<ApiImpalaQuery> queries = response.getQueries();
    for (ApiImpalaQuery query : queries) {
      System.out.println("\t query state=" + query.getQueryState());
      System.out.println("\t query=" + query.getStatement());
    }
  }

  /**
   * Load a Java properties file from the classpath.
   *
   * @param propfile Name of file containing configuration properties.
   * @return Populated Properties object.
   * @throws IOException if error occurs loading property file.
   */
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
