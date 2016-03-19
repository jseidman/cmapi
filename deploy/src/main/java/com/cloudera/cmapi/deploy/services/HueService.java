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

public class HueService extends ClusterService {

  private static String SERVICE_TYPE="HUE";
  private enum RoleType { HUE_SERVER, KT_RENEWER };
  private static final Logger LOG = Logger.getLogger(HueService.class);

  public HueService(Wini config, ServicesResourceV10 servicesResource) {

    super(config, servicesResource);
    setName(config.get(Constants.HUE_CONFIG_SECTION,
                       Constants.HUE_SERVICE_NAME_PARAMETER));

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
      ApiServiceList hueServices = new ApiServiceList();
      ApiService hueService = new ApiService();
      hueService.setType(SERVICE_TYPE);
      hueService.setName(name);

      Ini.Section serviceConfigSection = 
        config.get(Constants.HUE_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      hueService.setConfig(serviceConfig);
      
      List<ApiRole> hueRoles = new ArrayList<ApiRole>();
      
      LOG.info("Adding Broker roles...");
      hueRoles.addAll(createRoles(RoleType.HUE_SERVER.name(), null,
                                    config.get(Constants.HUE_CONFIG_SECTION,
                                               Constants.HUE_SERVER_HOST_PARAMETER).split(",")));

      for (ApiRole role : hueRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }
      
      hueService.setRoles(hueRoles);
      hueServices.add(hueService);
      servicesResource.createServices(hueServices);

      LOG.info("HUE services successfully created, now setting role configurations...");
  
      updateRoleConfigurations(config, servicesResource);
    }
  }

  public boolean preStartInitialization() {
    return true;
  }

  public boolean postStartInitialization() {
    return true;
  }
}