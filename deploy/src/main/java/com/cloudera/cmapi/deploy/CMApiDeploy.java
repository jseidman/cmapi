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
package com.cloudera.cmapi.deploy;

import com.cloudera.api.ClouderaManagerClientBuilder;
import com.cloudera.api.v10.RootResourceV10;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;

import org.apache.log4j.Logger;

import org.ini4j.Wini;

/**
 * Main driver for application to deploy Cloudera clusters via the
 * Cloudera Manager API. This class makes calls to the relevant objects to step
 * through the deployment process:
 * <p><ul>
 * <li> Initialize clusters (set cluster name, assign hosts, etc.).
 * <li> Deploy management services.
 * <li> Deploy Parcels.
 * <li> Deploy and start cluster services.
 * </ul><p>
 */
public class CMApiDeploy {

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(CMApiDeploy.class);

  /**
   * Load configuration info from disk, get a reference to the CM API root
   * resource object, then create management services and clusters.
   *
   * @param args Command line arguments.
   */
  public static void main(final String[] args) {

    String configFile = System.getProperty("cmapi.ini.file");
    CMApiDeploy deploy = null;

    Wini config = null;

    try {
      deploy = new CMApiDeploy();
      config = deploy.getConfig(configFile);
    } catch (IOException e) {
      LOG.error("Caught exception reading configuration from " + configFile +
                ", exception was " + e.getMessage());
      System.exit(1);
    }

    RootResourceV10 apiRoot = deploy.getRootResource(config);
    LOG.info("Successfully created root resource");

    CMServer cm = new CMServer(config, apiRoot);
    LOG.info("Successfully created CM server resource, initializing clusters...");
    cm.initializeClusters();
    LOG.info("Successfully initialized clusters, deploying management service...");
    cm.deployManagementService();
    cm.startManagementService();
    cm.deployParcels();
    cm.deployClusters();
  }

  /**
   * Get a reference to the object providing access to the CM API
   * root namespace.
   *
   * @param config Object containing required config parameters.
   *
   * @return Root resource object.
   */
  private RootResourceV10 getRootResource(final Wini config) {

    RootResourceV10 apiRoot = new ClouderaManagerClientBuilder()
      .withHost(config.get("CM", Constants.CM_PUBLIC_HOSTNAME_PARAMETER))
      .withPort(7180)
      .withUsernamePassword(config.get("CM", Constants.CM_USERNAME_PARAMETER),
                            config.get("CM", Constants.CM_PASSWORD_PARAMETER))
      .build()
      .getRootV10();

    return apiRoot;
  }

  /**
   * Load config file from disk and create configuration object.
   *
   * @param inifile Name of file containing configuration parameters.
   *
   * @return Object encapsulating configuration parameters.
   *
   * @throws IOException if error occurs loading file.
   */
  public final Wini getConfig(final String inifile)
    throws IOException {

    InputStream in = null;
    Wini ini = null;

    try {
      in = getClass().getClassLoader().getResourceAsStream(inifile);
      if (in != null) {
        ini = new Wini(in);
      } else {
        throw new FileNotFoundException(inifile + " not found");
      }
    } finally {
      if (in != null) {
        in.close();
      }
    }

    return ini;
  }
}
