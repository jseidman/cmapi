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

import com.cloudera.api.model.ApiBulkCommandList;
import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.model.ApiConfig;
import com.cloudera.api.model.ApiConfigList;
import com.cloudera.api.model.ApiHostRef;
import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiRoleConfigGroup;
import com.cloudera.api.model.ApiRoleConfigGroupRef;
import com.cloudera.api.model.ApiRoleNameList;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.model.ApiServiceConfig;
import com.cloudera.api.model.ApiServiceList;
import com.cloudera.api.v10.ServicesResourceV10;

import com.cloudera.cmapi.deploy.CMServer;
import com.cloudera.cmapi.deploy.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

public class OozieService extends ClusterService {

  private static String SERVICE_TYPE="OOZIE";
  private enum RoleType { OOZIE_SERVER };
  private static final Logger LOG = Logger.getLogger(OozieService.class);

  public OozieService(Wini config, ServicesResourceV10 servicesResource) {
    super(config, servicesResource);
    setName(config.get(Constants.OOZIE_CONFIG_SECTION, 
                       Constants.OOZIE_SERVICE_NAME_PARAMETER));
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
      ApiServiceList oozieServices = new ApiServiceList();
      ApiService oozieService = new ApiService();
      oozieService.setType(SERVICE_TYPE);
      oozieService.setName(name);

      // Set service configuration:
      Ini.Section serviceConfigSection = 
        config.get(Constants.OOZIE_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      oozieService.setConfig(serviceConfig);
      
      // Create service roles:
      List<ApiRole> oozieRoles = new ArrayList<ApiRole>();
      
      LOG.info("Adding Oozie Server role...");
      oozieRoles.addAll(createRoles(RoleType.OOZIE_SERVER.name(), null,
                                   config.get(Constants.OOZIE_CONFIG_SECTION, 
                                              Constants.OOZIE_SERVER_HOST_PARAMETER).split(",")));

      for (ApiRole role : oozieRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }
      
      oozieService.setRoles(oozieRoles);
      oozieServices.add(oozieService);
      servicesResource.createServices(oozieServices);

      LOG.info("Oozie services successfully created, now setting role configurations...");
  
      updateRoleConfigurations(config, servicesResource);
    }
  }

  /**
   * Perform initialization tasks before starting the Oozie service.
   */
  public boolean preStartInitialization() {
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
   */
  public boolean postStartInitialization() {
    return true;
  }
}