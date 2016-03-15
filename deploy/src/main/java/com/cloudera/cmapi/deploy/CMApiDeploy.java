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
import com.cloudera.api.DataView;
import com.cloudera.api.model.ApiCluster;
import com.cloudera.api.model.ApiClusterList;
import com.cloudera.api.model.ApiClusterVersion;
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

import org.apache.log4j.Logger;

import org.ini4j.Wini;

public class CMApiDeploy {

  private static final Logger LOG = Logger.getLogger(CMApiDeploy.class);

  public static void main( String[] args ) {
    
    String configFile = System.getProperty("cmapi.ini.file");
    CMApiDeploy deploy = null;

    Wini config = null;

    try {
      deploy = new CMApiDeploy();
      config = deploy.getConfig(configFile);
    } catch(IOException e) {
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

  private RootResourceV10 getRootResource(Wini config) {

    RootResourceV10 apiRoot = new ClouderaManagerClientBuilder()
      .withHost(config.get("CM", Constants.CM_PUBLIC_HOSTNAME_PARAMETER))
      .withPort(7180)
      .withUsernamePassword(config.get("CM", Constants.CM_USERNAME_PARAMETER ),
                            config.get("CM", Constants.CM_PASSWORD_PARAMETER))
      .build()
      .getRootV10();

    return apiRoot;
  }

  public Wini getConfig(String inifile) 
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
      in.close();
    }
       
    return ini;
  }
}
