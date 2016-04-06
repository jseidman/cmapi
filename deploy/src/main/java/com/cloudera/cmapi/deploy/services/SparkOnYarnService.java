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
 * Class to manage Spark on Yarn service deployment.
 */
public class SparkOnYarnService extends ClusterService {

  /**
   * Service type. This needs to match a valid CDH service type.
   */
  private static final String SERVICE_TYPE = "SPARK_ON_YARN";

  /**
   * Role types associated with this service.
   */
  private enum RoleType { SPARK_YARN_HISTORY_SERVER, GATEWAY };

  /**
   * Log4j logger.
   */
  private static final Logger LOG =
    Logger.getLogger(SparkOnYarnService.class);

  /**
   * Constructor initializes required parameters to execute deployment.
   *
   * @param config Configuration parameters.
   * @param servicesResource Cloudera Manager API object providing access
   * to functionality for configuring, creating, etc. services on a cluster.
   */
  public SparkOnYarnService(final Wini config,
                            final ServicesResourceV10 servicesResource) {

    super(config, servicesResource);
    setName(config.get(Constants.SPARK_CONFIG_SECTION,
                       Constants.SPARK_SERVICE_NAME_PARAMETER));

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
      ApiServiceList sparkServices = new ApiServiceList();
      ApiService sparkService = new ApiService();
      sparkService.setType(SERVICE_TYPE);
      sparkService.setName(name);

      Ini.Section serviceConfigSection =
        config.get(Constants.SPARK_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      sparkService.setConfig(serviceConfig);

      List<ApiRole> sparkRoles = new ArrayList<ApiRole>();

      LOG.info("Adding History Server role...");
      sparkRoles.addAll(createRoles(RoleType.SPARK_YARN_HISTORY_SERVER.name(), null,
                                   config.get(Constants.SPARK_CONFIG_SECTION,
                                              Constants.SPARK_HISTORYSERVER_HOST_PARAMETER).split(",")));

      LOG.info("Adding Gateway roles...");
      sparkRoles.addAll(createRoles(RoleType.GATEWAY.name(), null,
                                    config.get(Constants.SPARK_CONFIG_SECTION,
                                               Constants.SPARK_GATEWAY_HOSTS_PARAMETER).split(",")));

      for (ApiRole role : sparkRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }

      sparkService.setRoles(sparkRoles);
      sparkServices.add(sparkService);
      // /api/v1/clusters/{clusterName}/services
      servicesResource.createServices(sparkServices);

      LOG.info("Spark on YARN services successfully created, " +
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
    // Note the use of the firstRun() API call here, which will execute the
    // following commands:
    //   CreateSparkUserDirCommand
    //   CreateSparkHistoryDirCommand
    //   SparkUploadJarServiceCommand
    // Note that these commands can be run separately via the
    // ServicesResource.serviceCommandByName() method, but using firstRun() is
    // a convenient shortcut. firstRun() also has the advantage of ensuring tha
    // dependent services are started before executing these commands.
    LOG.info("Executing firstRun command for Spark");
    ApiCommand command = servicesResource.firstRun(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("firstRun command for Spark completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  /**
   * Perform any required setup tasks for this service after starting.
   * No tasks are required for this service.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean postStartInitialization() {
    return true;
  }
}
