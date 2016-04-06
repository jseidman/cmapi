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
 * Class to manage Oozie service deployment.
 */
public class OozieService extends ClusterService {

  /**
   * Service type. This needs to match a valid CDH service type.
   */
  private static final String SERVICE_TYPE = "OOZIE";

  /**
   * Role types associated with this service.
   */
  private enum RoleType { OOZIE_SERVER };

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(OozieService.class);

  /**
   * Constructor initializes required parameters to execute deployment.
   *
   * @param config Configuration parameters.
   * @param servicesResource Cloudera Manager API object providing access
   * to functionality for configuring, creating, etc. services on a cluster.
   */
  public OozieService(final Wini config,
                      final ServicesResourceV10 servicesResource) {
    super(config, servicesResource);
    setName(config.get(Constants.OOZIE_CONFIG_SECTION,
                       Constants.OOZIE_SERVICE_NAME_PARAMETER));
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
      ApiServiceList oozieServices = new ApiServiceList();
      ApiService oozieService = new ApiService();
      oozieService.setType(SERVICE_TYPE);
      oozieService.setName(name);

      // Set service configuration:
      Ini.Section serviceConfigSection =
        config.get(Constants.OOZIE_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig =
        getServiceConfig(serviceConfigSection);
      oozieService.setConfig(serviceConfig);

      // Create service roles:
      List<ApiRole> oozieRoles = new ArrayList<ApiRole>();

      LOG.info("Adding Oozie Server role...");
      oozieRoles.addAll(createRoles(RoleType.OOZIE_SERVER.name(), null,
                                   config.get(Constants.OOZIE_CONFIG_SECTION,
                                              Constants.OOZIE_SERVER_HOST_PARAMETER).split(",")));

      for (ApiRole role : oozieRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" +
                  role.getHostRef());
      }

      oozieService.setRoles(oozieRoles);
      oozieServices.add(oozieService);
      // /api/v1/clusters/{clusterName}/services
      servicesResource.createServices(oozieServices);

      LOG.info("Oozie services successfully created, " +
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
    boolean status = false;
    LOG.info("Installing Oozie ShareLib...");
    ApiCommand command = servicesResource.installOozieShareLib(name);
    status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Install Oozie ShareLib completed " +
             (status ? "successfully" : "unsuccessfully"));
    LOG.info("Creating Oozie DB...");
    command = servicesResource.createOozieDb(name);
    status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Install Oozie DB completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  /**
   * Perform any required setup tasks for this service after starting.
   * No post-start tasks are required for the Oozie service.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean postStartInitialization() {
    return true;
  }
}
