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

public class ZooKeeperService extends ClusterService {

  private static final String SERVICE_TYPE = "ZOOKEEPER";
  private static final String ZK_ROLE_TYPE="SERVER";
  private static final Logger LOG = Logger.getLogger(ZooKeeperService.class);
  
  public ZooKeeperService(Wini config, ServicesResourceV10 servicesResource) {
    super(config, servicesResource);
    setName(config.get(Constants.CLUSTER_CONFIG_SECTION,
                       Constants.ZOOKEEPER_NAME_PARAMETER));
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
      LOG.info(SERVICE_TYPE + " service already deployed. Skipping...");
    } else {
      LOG.info("Deploying " +  SERVICE_TYPE + " service...");
      ApiServiceList apiServices = new ApiServiceList();
      ApiService zkService = new ApiService();
      zkService.setType(SERVICE_TYPE);
      zkService.setName(name);
      
      // Set service configuration:
      Ini.Section serviceConfigSection = 
        config.get(Constants.ZOOKEEPER_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig = getServiceConfig(serviceConfigSection);
      zkService.setConfig(serviceConfig);
 
      // Create service roles:
      LOG.info("Adding ZooKeeper roles...");
      List<ApiRole> zkRoles = new ArrayList<ApiRole>();
      zkRoles.addAll(createRoles(ZK_ROLE_TYPE, null,
                                 config.get(Constants.CLUSTER_CONFIG_SECTION,
                                            Constants.ZOOKEEPER_HOSTS_PARAMETER).split(",")));
      zkService.setRoles(zkRoles);
      apiServices.add(zkService);
      servicesResource.createServices(apiServices);
      LOG.info("Successfully added ZooKeeper service " + name +
               ", now setting role configurations...");

      updateRoleConfigurations(config, servicesResource);
    }
  }

  public boolean preStartInitialization() {
    boolean status = false;
    LOG.info("Running ZooKeeper server initialization...");
    ApiCommand command = servicesResource.zooKeeperInitCommand(name);
    status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("ZooKeeper server initialization completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  public boolean postStartInitialization() {
    return true;  
  }
}