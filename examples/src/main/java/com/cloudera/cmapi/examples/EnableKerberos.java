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
import com.cloudera.api.DataView;
import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.model.ApiConfig;
import com.cloudera.api.model.ApiConfigList;
import com.cloudera.api.model.ApiConfigureForKerberosArguments;
import com.cloudera.api.v11.ClouderaManagerResourceV11;
import com.cloudera.api.v11.ClustersResourceV11;
import com.cloudera.api.v11.RootResourceV11;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Enable Kerberos on a CDH cluster via the Cloudera Manager API.
 *
 * This class assumes the following:
 * <p><ul>
 * <li> An existing Cloudera Manager managed CDH deployment.
 * <li> A correctly deployed and configured Kerberos KDC. This class has been
 * tested with MIT Kerberos.
 * </ul><p>
 *
 * Note that this class assumes that the Kerberos KDC config (krb5.conf) is
 * already deployed to all cluster hosts. Alternatively, Cloudera Manager can
 * be used to configure and deploy the krb5.conf. See comments in the code
 * for updates required to do this.
 * <p>
 *
 * Provided with correctly deployed Cloudera and Kerberos installations,
 * this class performs the following steps:
 * <p><ul>
 * <li> Set required Kerberos configuration parameters in Cloudera Manager
 * such as the KDC host, default realm, encryption types, etc.
 * <li> Stop cluster and manager services.
 * <li> Set the credentials for the KDC account manager.
 * <li> Execute the API call to configure the cluster for Kerberos.
 * <li> Wait for the generate credentials command to complete.
 * <li> Start the cluster and manager services.
 * <li> Deploy client configuration.
 * </ul></p>
 *
 * Note that the above mirrors the steps taken to manually enable Kerberos
 * via the Cloudera Manager UI. See:
 * http://www.cloudera.com/documentation/enterprise/latest/topics/cm_sg_using_cm_sec_config.html.
 * <p>
 *
 * This class expects a Java Properties file defining the following parameters:
 * <p><ul>
 * <li> Cloudera Manager Hostname.
 * <li> Cloudera Manager administrative credentials.
 * <li> DataNode Transceiver and Web UI ports.
 * <li> Credentials for KDC account manager.
 * <li> KDC type.
 * <li> KDC hostname.
 * <li> Default security realm.
 * <li> Supported encryption types.
 * </ul><p>
 *
 * See the documentation referenced above for more details on these parameters.
 * <p>
 * Note that this requires version 11 of the CM API.
 * <p>
 *
 * To build and execute:
 * <p><ul>
 * <li> mvn package
 * <li> mvn exec:java -Dcmapi.properties.file=cmexamples.properties
 * -Dexec.mainClass="com.cloudera.cmapi.examples.EnableKerberos"
 * </ul>
 * or
 * <ul>
 * <li> java -cp target/examples-1.0-SNAPSHOT-executable.jar
 * -Dcmapi.properties.file=cmexamples.properties
 * com.cloudera.cmapi.examples.EnableKerberos
 * </ul>
 */
public class EnableKerberos {

  /**
   * Cloudera Manager API root resource handle.
   */
  private static RootResourceV11 apiRoot;

  /**
   * Label used by CM for the generate credentials command.
   */
  private static final String GENERATE_CREDENTIALS_COMMAND
    = "GenerateCredentials";

  /**
   * Execute the steps to enable Kerberos.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) {

    EnableKerberos test = null;

    // Get configuration parameters:
    String propfile = System.getProperty("cmapi.properties.file");
    Properties cmprops = new Properties();
    try {
      test = new EnableKerberos();
      cmprops = test.getProperties(propfile);
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Get handle to the root resource. This is required for getting access
    // to the REST namespace:
    apiRoot = new ClouderaManagerClientBuilder()
      .withHost((String) cmprops.get("cmhost"))
      .withPort(7180)
      .withUsernamePassword((String) cmprops.get("cmuser"),
                            (String) cmprops.get("cmpass"))
      .build()
      .getRootV11();

    // Use the root handle to get the resource object representing the
    // Cloudera Manager instance:
    ClouderaManagerResourceV11 cmResource =
      apiRoot.getClouderaManagerResource();

    // Use the CM Resource object to set required Kerberos specific config
    // parameters.
    // We're just setting the minimal required parameters here. Also note
    // that these parameters are based on an MIT KDC, and will vary when
    // using AD. To see all available config parameters, including the
    // names to use when populating values, use the following API call:
    // CM_HOST:7180/api/v11/cm/config?view=full. 
    // This call can be executed from a browser, or via a command line
    // tool like wget or curl.
    System.out.println("Configuring Kerberos params in CM...");
    ApiConfigList cmConfigList = new ApiConfigList();
    // This actually defaults to "MIT KDC":
    cmConfigList.add(new ApiConfig("KDC_TYPE",
                                   (String) cmprops.get("kdc_type")));
    cmConfigList.add(new ApiConfig("KDC_HOST",
                                   (String) cmprops.get("kdc_host")));
    cmConfigList.add(new ApiConfig("SECURITY_REALM",
                                   (String) cmprops.get("security_realm")));
    // This should be a space-delimited list of encryption types:
    cmConfigList.add(new ApiConfig("KRB_ENC_TYPES",
                                   (String) cmprops.get("krb_enc_types")));
    // Use the following to have CM deploy the Kerberos config (krb5.conf) to
    // cluster hosts. This also requires a deployClusterConfig call before
    // starting services (see below):
    // cmConfigList.add(new ApiConfig("KRB_MANAGE_KRB5_CONF", "true");
    // /api/v11/cm/config
    cmResource.updateConfig(cmConfigList);

    // Get the cluster resource object:
    ClustersResourceV11 clustersResource = apiRoot.getClustersResource();
    // Use the cluster resource object to get the name of the cluster being
    // secured. Note that we're just assuming there's a single cluster being
    // managed, since we're just grabbing the first value returned. This
    // would need to be modified if the CM instance is managing multiple
    // clusters. Alternatively we could pass the cluster name in as a
    // config parameter.
    //  /api/v11/clusters
    String clusterName =
      clustersResource.readClusters(DataView.SUMMARY).get(0).getName();
    System.out.println("Cluster name=" + clusterName);

    // Stop cluster services. In a more robust implementation we'd probably
    // want to first check that services are currently running:
    System.out.println("Stopping cluster services...");
    // /api/v11/clusters/{clusterName}/commands/stop
    ApiCommand command = clustersResource.stopCommand(clusterName);
    Boolean status = waitForCommand(command);
    System.out.println("Stop cluster command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // Stop management services:
    System.out.println("Stopping management services...");
    // /api/v11/cm/service/commands/stop
    command = cmResource.getMgmtServiceResource().stopCommand();
    status = waitForCommand(command);
    System.out.println("Stop management services command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // Set the credentials for the KDC account manager:
    System.out.println("Setting manager credentials...");
    // /api/v11/cm/commands/importAdminCredentials
    command =
      cmResource.importAdminCredentials((String) cmprops.get("principal"),
                                        (String) cmprops.get("password"));
    status = waitForCommand(command);
    System.out.println("Import credentials command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // Then create config object for Kerberos parameters and execute the
    // call to configure the cluster for Kerberos:
    ApiConfigureForKerberosArguments kerberosArguments =
      new ApiConfigureForKerberosArguments();
    kerberosArguments.setDatanodeTransceiverPort(Long.valueOf((String) cmprops.get("dn_trasceiver_port")));
    kerberosArguments.setDatanodeWebPort(Long.valueOf((String) cmprops.get("dn_web_port")));
    // /api/v11/clusters/{clusterName}/commands/configureForKerberos
    command = clustersResource.configureForKerberos(clusterName,
                                                    kerberosArguments);
    status = waitForCommand(command);
    System.out.println("Configure Kerberos command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // After the configureForKerberos call completes, it will trigger a
    // generate credentials command, so we'll allow some time for this
    // command to start, then we'll wait for it's completion before
    // restarting everything:
    System.out.println("Gonna sleep for a bit to wait for " +
                       "generate credentials command to start...");
    try {
          Thread.sleep(15000);
        } catch (InterruptedException e) {
      // We'll just ignore...
    }

    // Find the generate credentials command and then wait for it to
    // complete. Note that this command is associated with the
    // Cloudera Manager resource:
    List<ApiCommand> cmCommands =
      cmResource.listActiveCommands(DataView.FULL).getCommands();
    for (ApiCommand cmCommand : cmCommands) {
      if (cmCommand.getName().equals(GENERATE_CREDENTIALS_COMMAND)) {
        command = cmCommand;
        break;
      }
    }
    status = waitForCommand(command);
    System.out.println("Generate credentials command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // Required if using CM to manage krb5.conf. Note that additional work
    // needs to be done to add the list of cluster hosts to the call.
    // command = clustersResource.deployClusterClientConfig(clusterName,
    //                                                      clusterHosts);

    // Start cluster services:
    System.out.println("Starting cluster services...");
    // /api/v11/clusters/{clusterName}/commands/start
    command = clustersResource.startCommand(clusterName);
    status = waitForCommand(command);
    System.out.println("Start cluster command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // Start management services:
    System.out.println("Starting management services...");
    // /api/v11/cm/service/commands/start
    command = cmResource.getMgmtServiceResource().startCommand();
    status = waitForCommand(command);
    System.out.println("Start management services command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    // Deploy client configs:
    // /api/v11/clusters/{clusterName}/commands/deployClientConfig
    command = clustersResource.deployClientConfig(clusterName);
    status = waitForCommand(command);
    System.out.println("Deploy client config command completed, status = " +
                       (status ? "successful" : "uh-oh"));

    System.out.println("Kerberos successfully enabled on " + clusterName);
  }

  /**
   * Wait for a Cloudera Manager command to complete running, and then return
   * a flag indicating whether the command completed successfully or not.
   *
   * @param command CM API command to wait for.
   * @return True if command was successful, False otherwise.
   */
  private static Boolean waitForCommand(ApiCommand command) {
    while (apiRoot.getCommandsResource().readCommand(command.getId()).isActive()) {
      System.out.println("Waiting for " + command.getName() +
                         " command to complete...");
      try {
          Thread.sleep(15000);
        } catch (InterruptedException e) {
      }
    }

    return apiRoot.getCommandsResource().readCommand(command.getId()).getSuccess();
  }

  /**
   * Load a Java properties file from the classpath.
   *
   * @param propfile Name of file containing configuration properties.
   * @return Populated Properties object.
   * @throws IOException if error occurs loading property file.
   */
  private Properties getProperties(String propfile)
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

