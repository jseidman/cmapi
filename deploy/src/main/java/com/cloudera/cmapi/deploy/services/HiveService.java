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
package com.cloudera.cmapi.deploy.services;

import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.model.ApiConfig;
import com.cloudera.api.model.ApiConfigList;
import com.cloudera.api.model.ApiHostRef;
import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiRoleConfigGroup;
import com.cloudera.api.model.ApiRoleConfigGroupRef;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.model.ApiServiceConfig;
import com.cloudera.api.model.ApiServiceList;
import com.cloudera.api.v10.ServicesResourceV10;

import com.cloudera.cmapi.deploy.Constants;
import com.cloudera.cmapi.deploy.CMServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

public class HiveService extends ClusterService {

  private static String SERVICE_TYPE="HIVE";
  private enum RoleType { HIVEMETASTORE, HIVESERVER2, GATEWAY };
  private static final Logger LOG = Logger.getLogger(HiveService.class);

  public HiveService(Wini config, ServicesResourceV10 servicesResource) {
    super(config, servicesResource);
    setName(config.get(Constants.HIVE_CONFIG_SECTION,
                       Constants.HIVE_SERVICE_NAME_PARAMETER));
    setServiceType(SERVICE_TYPE);
  }

  public void deploy() {
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
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }
      
      hiveService.setRoles(hiveRoles);
      hiveServices.add(hiveService);
      servicesResource.createServices(hiveServices);

      LOG.info("Hive services successfully created, now setting role configurations...");
  
      updateRoleConfigurations(config, servicesResource);
    }
  }

  public boolean preStartInitialization() {
    return true;
  }

 /**
   * Perform initialization tasks for Hive service after starting. For Hive 
   * this is running the command to create the Hive warehouse directory.
   *
   * TODO: consider whether this should be moved to HDFS post start method.
   */
  public boolean postStartInitialization() {
    LOG.info("Creating Hive warehouse directory");
    // /clusters/{clusterName}/services/{serviceName}/commands/hiveCreateHiveWarehouse
    ApiCommand command = servicesResource.createHiveWarehouseCommand(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Create Hive warehouse directory command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }
}