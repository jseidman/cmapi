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


import com.cloudera.api.DataView;
import com.cloudera.api.model.ApiHostRef;
import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v8.ClouderaManagerResourceV8;
import com.cloudera.api.v8.MgmtServiceResourceV8;

import com.cloudera.cmapi.deploy.services.ManagementService;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import org.ini4j.Wini;

public class CMServer {

  /**
   * Host name for Cloudera Manager server.
   */
  private String cmhost;

  /**
   * Cloudera Manager resource object.
   */
  private ClouderaManagerResourceV8 cmResource;

  /**
   * One or more clusters managed by this instance.
   */
  private List<Cluster> clusters; 

  /**
   * Cloudera Manager management service associated with this instance.
   */
  //private Service cmService;

  /**
   * Config object containing parameters required for deploying, etc.
   */
  private Wini config;

  /**
   * Top level resource object.
   */
  private RootResourceV10 apiRoot;

  private static final Logger LOG = Logger.getLogger(CMServer.class);

  public CMServer(Wini config, RootResourceV10 apiRoot) {
    this.config = config;
    this.apiRoot = apiRoot;
    
    cmhost = config.get("CM", Constants.CM_PUBLIC_HOSTNAME_PARAMETER);
    cmResource = apiRoot.getClouderaManagerResource();

    clusters = new ArrayList<Cluster>();
  }

  public void initializeClusters() {
    Cluster cluster = new Cluster(apiRoot, config);
    cluster.provisionCluster();
    clusters.add(cluster);
  }

  public void deployManagementService() {

    ManagementService mgmtService = new ManagementService();
    mgmtService.deploy(config, cmResource);
  }

  public void deployParcels() {

    LOG.info("Deploying parcels");
    for (Cluster cluster : clusters) {
      LOG.info("Deploying parcels for cluster " + cluster.getName());
      cluster.provisionParcels();
    }
  }
}