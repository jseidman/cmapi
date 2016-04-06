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
package com.cloudera.cmapi.deploy.services;

import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.model.ApiServiceConfig;
import com.cloudera.api.model.ApiServiceList;
import com.cloudera.api.v10.ServicesResourceV10;

import com.cloudera.cmapi.deploy.CMServer;
import com.cloudera.cmapi.deploy.Constants;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

/**
 * Class to manage Sqoop2 service deployment.
 */
public class Sqoop2Service extends ClusterService {

  /**
   * Service type. This needs to match a valid CDH service type.
   */
  private static final String SERVICE_TYPE = "SQOOP";

  /**
   * Role types associated with this service.
   */
  private enum RoleType { SQOOP_SERVER };

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(HueService.class);

  /**
   * Constructor initializes required parameters to execute deployment.
   *
   * @param config Configuration parameters.
   * @param servicesResource Cloudera Manager API object providing access
   * to functionality for configuring, creating, etc. services on a cluster.
   */
  public Sqoop2Service(final Wini config,
                       final ServicesResourceV10 servicesResource) {

    super(config, servicesResource);
    setName(config.get(Constants.SQOOP2_CONFIG_SECTION,
                       Constants.SQOOP2_SERVICE_NAME_PARAMETER));

    setServiceType(SERVICE_TYPE);
  }

  /**
   * Deploy service and associated roles.
   */
  public final void deploy() {

    // Make sure service isn't already deployed:
    boolean provisionRequired = false;
    try {
      provisionRequired = servicesResource.readService(name) == null;
    } catch (Exception e) {
      provisionRequired = true;
    }

    if (!provisionRequired) {
      LOG.info(SERVICE_TYPE + " services already deployed. Skipping...");
    } else {
      LOG.info("Deploying " +  SERVICE_TYPE + " service...");
      ApiServiceList sqoop2Services = new ApiServiceList();
      ApiService sqoop2Service = new ApiService();
      sqoop2Service.setType(SERVICE_TYPE);
      sqoop2Service.setName(name);

      Ini.Section serviceConfigSection =
        config.get(Constants.SQOOP2_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      sqoop2Service.setConfig(serviceConfig);

      List<ApiRole> sqoop2Roles = new ArrayList<ApiRole>();

      LOG.info("Adding Sqoop 2 Server roles...");
      sqoop2Roles.addAll(createRoles(RoleType.SQOOP_SERVER.name(), null,
                                     config.get(Constants.SQOOP2_CONFIG_SECTION,
                                                Constants.SQOOP2_SERVER_HOST_PARAMETER).split(",")));

      sqoop2Service.setRoles(sqoop2Roles);
      sqoop2Services.add(sqoop2Service);
      // /api/v1/clusters/{clusterName}/services
      servicesResource.createServices(sqoop2Services);

      LOG.info("Sqoop 2 services successfully created, " +
               "now setting role configurations...");

      updateRoleConfigurations();
    }
  }

  /**
   * Perform any required setup tasks for this service before starting.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean preStartInitialization() {
    LOG.info("Running Sqoop2 create DB command...");
    ApiCommand command =
      servicesResource.sqoopCreateDatabaseTablesCommand(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Create Sqoop DB command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

 /**
   * Perform any required setup tasks for this service after starting.
   * In the case of the Flume service no tasks are required.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean postStartInitialization() {
    return true;
  }
}
