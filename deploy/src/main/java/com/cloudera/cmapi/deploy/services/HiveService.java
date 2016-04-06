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

import com.cloudera.cmapi.deploy.Constants;
import com.cloudera.cmapi.deploy.CMServer;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

/**
 * Class to manage Hive service deployment.
 */
public class HiveService extends ClusterService {

  /**
   * Service type. This needs to match a valid CDH service type.
   */
  private static final String SERVICE_TYPE = "HIVE";

  /**
   * Role types associated with this service.
   */
  private enum RoleType { HIVEMETASTORE, HIVESERVER2, GATEWAY };

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(HiveService.class);

  /**
   * Constructor initializes required parameters to execute deployment.
   *
   * @param config Configuration parameters.
   * @param servicesResource Cloudera Manager API object providing access
   * to functionality for configuring, creating, etc. services on a cluster.
   */
  public HiveService(final Wini config,
                     final ServicesResourceV10 servicesResource) {
    super(config, servicesResource);
    setName(config.get(Constants.HIVE_CONFIG_SECTION,
                       Constants.HIVE_SERVICE_NAME_PARAMETER));
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
      ApiServiceList hiveServices = new ApiServiceList();
      ApiService hiveService = new ApiService();
      hiveService.setType(SERVICE_TYPE);
      hiveService.setName(name);

      Ini.Section serviceConfigSection =
        config.get(Constants.HIVE_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      hiveService.setConfig(serviceConfig);

      List<ApiRole> hiveRoles = new ArrayList<ApiRole>();

      LOG.info("Adding metastore role...");
      hiveRoles.addAll(createRoles(RoleType.HIVEMETASTORE.name(), null,
                                   config.get(Constants.HIVE_CONFIG_SECTION,
                                              Constants.HIVE_METASTORE_HOST_PARAMETER).split(",")));

      LOG.info("Adding HiveServer2 role...");
      hiveRoles.addAll(createRoles(RoleType.HIVESERVER2.name(), null,
                                   config.get(Constants.HIVE_CONFIG_SECTION,
                                              Constants.HIVE_HS2_HOSTS_PARAMETER).split(",")));

      LOG.info("Adding Gateway roles...");
      hiveRoles.addAll(createRoles(RoleType.GATEWAY.name(), null,
                                   config.get(Constants.HIVE_CONFIG_SECTION,
                                              Constants.HIVE_GATEWAY_HOSTS_PARAMETER).split(",")));

      for (ApiRole role : hiveRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" +
                  role.getHostRef());
      }

      hiveService.setRoles(hiveRoles);
      hiveServices.add(hiveService);
      // /api/v1/clusters/{clusterName}/services
      servicesResource.createServices(hiveServices);

      LOG.info("Hive services successfully created, now setting role " +
               "configurations...");

      updateRoleConfigurations();
    }
  }

  /**
   * Perform any required setup tasks for this service before starting.
   * In the case of the Hive service no tasks are required.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean preStartInitialization() {
    return true;
  }

 /**
   * Perform initialization tasks for Hive service after starting. For Hive
   * this is running the command to create the Hive warehouse directory.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean postStartInitialization() {
    LOG.info("Creating Hive warehouse directory");
    // /clusters/{clusterName}/services/{serviceName}/commands/hiveCreateHiveWarehouse
    ApiCommand command = servicesResource.createHiveWarehouseCommand(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Create Hive warehouse directory command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }
}
