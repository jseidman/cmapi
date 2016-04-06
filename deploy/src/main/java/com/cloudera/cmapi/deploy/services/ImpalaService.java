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

import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.model.ApiServiceConfig;
import com.cloudera.api.model.ApiServiceList;
import com.cloudera.api.v10.ServicesResourceV10;

import com.cloudera.cmapi.deploy.Constants;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

/**
 * Class to manage Impala service deployment.
 */
public class ImpalaService extends ClusterService {

  /**
   * Service type. This needs to match a valid CDH service type.
   */
  private static final String SERVICE_TYPE = "IMPALA";

  /**
   * Role types associated with this service.
   */
  private enum RoleType { STATESTORE, CATALOGSERVER, IMPALAD };

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(ImpalaService.class);

  /**
   * Constructor initializes required parameters to execute deployment.
   *
   * @param config Configuration parameters.
   * @param servicesResource Cloudera Manager API object providing access
   * to functionality for configuring, creating, etc. services on a cluster.
   */
  public ImpalaService(final Wini config,
                       final ServicesResourceV10 servicesResource) {
    super(config, servicesResource);

    setName(config.get(Constants.IMPALA_CONFIG_SECTION,
                       Constants.IMPALA_SERVICE_NAME_PARAMETER));

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
      ApiServiceList impalaServices = new ApiServiceList();
      ApiService impalaService = new ApiService();
      impalaService.setType(SERVICE_TYPE);
      impalaService.setName(name);

      Ini.Section serviceConfigSection =
        config.get(Constants.IMPALA_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      impalaService.setConfig(serviceConfig);

      List<ApiRole> impalaRoles = new ArrayList<ApiRole>();

      LOG.info("Adding state store role...");
      impalaRoles.addAll(createRoles(RoleType.STATESTORE.name(), null,
                                   config.get(Constants.IMPALA_CONFIG_SECTION,
                                              Constants.IMPALA_STATESTORE_HOST_PARAMETER).split(",")));

      LOG.info("Adding catalog server role...");
      impalaRoles.addAll(createRoles(RoleType.CATALOGSERVER.name(), null,
                                   config.get(Constants.IMPALA_CONFIG_SECTION,
                                              Constants.IMPALA_CATALOGSERVER_HOST_PARAMETER).split(",")));

      LOG.info("Adding impalad roles...");
      impalaRoles.addAll(createRoles(RoleType.IMPALAD.name(), null,
                                   config.get(Constants.IMPALA_CONFIG_SECTION,
                                              Constants.IMPALA_IMPALAD_HOSTS_PARAMETER).split(",")));

      for (ApiRole role : impalaRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" +
                  role.getHostRef());
      }

      impalaService.setRoles(impalaRoles);
      impalaServices.add(impalaService);
      servicesResource.createServices(impalaServices);

      LOG.info("Impala services successfully created, now setting " +
               "role configurations...");

      updateRoleConfigurations();
    }
  }

  /**
   * Perform any required setup tasks for this service before starting.
   * For Impala no tasks are required.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */

  public final boolean preStartInitialization() {
    return true;
  }

  /**
   * Perform any required setup tasks for this service after starting.
   * For Impala no tasks are required.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean postStartInitialization() {
    return true;
  }
}
