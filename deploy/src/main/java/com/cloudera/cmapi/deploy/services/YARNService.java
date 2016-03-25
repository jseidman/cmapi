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

public class YARNService extends ClusterService {

  private static String SERVICE_TYPE="YARN";
  private enum RoleType { NODEMANAGER, RESOURCEMANAGER, JOBHISTORY, GATEWAY };
  private static final Logger LOG = Logger.getLogger(YARNService.class);

  public YARNService(Wini config, ServicesResourceV10 servicesResource) {

    super(config, servicesResource);
    setName(config.get(Constants.YARN_CONFIG_SECTION, 
                       Constants.YARN_SERVICE_NAME_PARAMETER));

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
      ApiServiceList yarnServices = new ApiServiceList();
      ApiService yarnService = new ApiService();
      yarnService.setType(SERVICE_TYPE);
      yarnService.setName(name);

      Ini.Section serviceConfigSection = 
        config.get(Constants.YARN_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      yarnService.setConfig(serviceConfig);
      
      List<ApiRole> yarnRoles = new ArrayList<ApiRole>();
      
      LOG.info("Adding ResourceManager role...");
      yarnRoles.addAll(createRoles(RoleType.RESOURCEMANAGER.name(), null,
                                   config.get(Constants.YARN_CONFIG_SECTION, 
                                              Constants.YARN_RESOURCEMANAGER_HOST_PARAMETER).split(",")));

      LOG.info("Adding JobHistory Server role...");
      yarnRoles.addAll(createRoles(RoleType.JOBHISTORY.name(), null,
                                   config.get(Constants.YARN_CONFIG_SECTION, 
                                              Constants.YARN_JOBHISTORY_SERVER_HOST_PARAMETER).split(",")));

      LOG.info("Adding NodeManager roles...");
      yarnRoles.addAll(createRoles(RoleType.NODEMANAGER.name(), null,
                                   config.get(Constants.YARN_CONFIG_SECTION, 
                                              Constants.YARN_NODEMANAGER_HOSTS_PARAMETER).split(",")));

      LOG.info("Adding Gateway roles...");
      yarnRoles.addAll(createRoles(RoleType.GATEWAY.name(), null,
                                   config.get(Constants.YARN_CONFIG_SECTION,
                                              Constants.YARN_GATEWAY_HOSTS_PARAMETER).split(",")));

      for (ApiRole role : yarnRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }
      
      yarnService.setRoles(yarnRoles);
      yarnServices.add(yarnService);
      servicesResource.createServices(yarnServices);

      LOG.info("YARN services successfully created, now setting role configurations...");
  
      updateRoleConfigurations();
    }
  }

  public boolean preStartInitialization() {
    // YARN initialization includes creating the MR2 job history directory and
    //  NodeManager remote application log directory. These commands can be
    // run separately via API calls, but here we're using the firstRun method
    // which encapsulates these commands. firstRun also facilitates these
    // commands by ensuring that HDFS is running, which is required before
    // creating these directories.
    LOG.info("Running YARN firstStart command...");
    ApiCommand command = servicesResource.firstRun(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("firstRun command for YARN completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  public boolean postStartInitialization() {
    return true;
  }
}