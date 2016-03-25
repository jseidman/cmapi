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


import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v8.ClouderaManagerResourceV8;

import com.cloudera.cmapi.deploy.services.ManagementService;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.ini4j.Wini;

/**
 * Class representing Cloudera Manager instance. Similar to the Cloudera Manager
 * Server, this class encapsulates and has responsibility for deploying
 * management services and clusters.
 */
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
   * Config object containing parameters required for deploying, etc.
   */
  private Wini config;

  /**
   * Top level resource object.
   */
  private static RootResourceV10 apiRoot;

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(CMServer.class);

  /**
   * Sleep time to wait for commands to complete execution.
   */
  private static final long SLEEP_LENGTH = 10000;

  /**
   * Constructor initializes parameters used by this class.
   *
   * @param config Object containing required config parameters.
   * @param apiRoot Object providing access to the CM API
   * root namespace.
   */
  public CMServer(final Wini config, final RootResourceV10 apiRoot) {
    this.config = config;
    this.apiRoot = apiRoot;

    cmhost = config.get("CM", Constants.CM_PUBLIC_HOSTNAME_PARAMETER);
    cmResource = apiRoot.getClouderaManagerResource();

    clusters = new ArrayList<Cluster>();
  }

  /**
   * Call methods on Cluster objects to perform required initialization
   * before deploying services to each cluster.
   */
  public final void initializeClusters() {
    Cluster cluster = new Cluster(apiRoot, config);
    cluster.provisionCluster();
    clusters.add(cluster);
  }

  /**
   * Deploy management services associated with this Cloudera Manager instance.
   */
  public final void deployManagementService() {

    ManagementService mgmtService = new ManagementService();
    mgmtService.deploy(config, cmResource);
  }

  /**
   * Start managment services.
   *
   * @return Success or failure of starting services.
   */
  public final boolean startManagementService() {
    // /api/v1/cm/service/commands/start
    ApiCommand command = cmResource.getMgmtServiceResource().startCommand();
    boolean status = waitForCommand(command).booleanValue();
    LOG.info("Start management services command completed, status = " +
             (status ? "successful" : "unsuccessful"));
    return status;
  }

  /**
   * Call method on each Cluster object to deploy required Parcels.
   */
  public final void deployParcels() {

    LOG.info("Deploying parcels");
    for (Cluster cluster : clusters) {
      LOG.info("Deploying parcels for cluster " + cluster.getName());
      cluster.provisionParcels();
    }
  }

  /**
   * Deploy services to intialized clusters, perform required initialization,
   * and then start clusters.
   */
  public final void deployClusters() {
    for (Cluster cluster : clusters) {
      LOG.info("Deploying services for cluster " + cluster.getName());
      cluster.provisionServices();
      // Note that we're using firstRun() when starting clusters, which
      // incorporates the pre- and post-initialization tasks that are required
      // when starting a new cluster. To change to manually run these tasks
      // un-comment the preInitializeServices() and postInitializeServices()
      // calls.
      //LOG.info("Running pre-start init tasks for cluster" + cluster.getName());
      //cluster.preInitializeServices();
      LOG.info("Starting cluster " + cluster.getName());
      cluster.startCluster();
      //LOG.info("Running post-start init tasks for cluster" + cluster.getName());
      //cluster.postInitializeServices();
      LOG.info("Deploying client configs for" + cluster.getName());
      cluster.deployClientConfigs();
    }
  }

  /**
   * Wait for a Cloudera Manager command to complete running, and then return
   * a flag indicating whether the command completed successfully or not.
   *
   * @param command Object encapsulating info on command being executed.
   *
   * @return Flag indicating success or failure of command execution.
   */
  public static Boolean waitForCommand(final ApiCommand command) {
    // /api/v1/commands/{commandId}
    while (apiRoot.getCommandsResource().readCommand(command.getId()).isActive()) {
      LOG.info("Waiting for " + command.getName() + " command to complete...");
      try {
          Thread.sleep(SLEEP_LENGTH);
        } catch (InterruptedException e) {
        LOG.warn(e.getMessage());
      }
    }
    LOG.info("Command " + command.getName() + " completed. Result = " +
             apiRoot.getCommandsResource().readCommand(command.getId()).getResultMessage());
    return apiRoot.getCommandsResource().readCommand(command.getId()).getSuccess();
  }
}
