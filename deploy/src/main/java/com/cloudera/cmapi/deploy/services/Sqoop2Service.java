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

import com.cloudera.cmapi.deploy.CMServer;
import com.cloudera.cmapi.deploy.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

public class Sqoop2Service extends ClusterService {

  private static String SERVICE_TYPE="SQOOP";
  private enum RoleType { SQOOP_SERVER };
  private static final Logger LOG = Logger.getLogger(HueService.class);

  public Sqoop2Service(Wini config, ServicesResourceV10 servicesResource) {

    super(config, servicesResource);
    setName(config.get(Constants.SQOOP2_CONFIG_SECTION,
                       Constants.SQOOP2_SERVICE_NAME_PARAMETER));

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
      servicesResource.createServices(sqoop2Services);

      LOG.info("Sqoop 2 services successfully created, now setting role configurations...");
  
      updateRoleConfigurations();
    }
  }

  public boolean preStartInitialization() {
    LOG.info("Running Sqoop2 create DB command...");
    ApiCommand command = servicesResource.sqoopCreateDatabaseTablesCommand(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Create Sqoop DB command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  public boolean postStartInitialization() {
    return true;
  }
}