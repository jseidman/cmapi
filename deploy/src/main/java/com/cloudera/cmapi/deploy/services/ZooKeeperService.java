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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

public class ZooKeeperService implements ClusterService {

  private static String SERVICE_TYPE = "ZOOKEEPER";
  private static final Logger LOG = Logger.getLogger(ZooKeeperService.class);

  public void deploy(Wini config, ServicesResourceV10 servicesResource) {

    String[] zkHosts = 
      config.get("CLUSTER", Constants.ZOOKEEPER_HOSTS_PARAMETER).split(",");
    ApiServiceList apiServices = new ApiServiceList();
    ApiService zkService = new ApiService();
    zkService.setType(SERVICE_TYPE);
    String name = config.get("CLUSTER", 
                             Constants.ZOOKEEPER_NAME_PARAMETER);
    LOG.debug("Setting ZooKeeper service name to " + name);
    zkService.setName(name);
    
    ApiServiceConfig serviceConfig = new ApiServiceConfig();
    Ini.Section serviceConfigSection = 
      config.get(Constants.ZOOKEEPER_SERVICE_CONFIG_SECTION);
    if (serviceConfigSection != null && serviceConfigSection.size() > 0) {
      for (Map.Entry<String, String> entry : serviceConfigSection.entrySet()) {
        LOG.debug("Adding ZooKeeper service config key/value: " +
                  entry.getKey() + "/" + entry.getValue());
        serviceConfig.add(new ApiConfig(entry.getKey(), entry.getValue()));
      }
    }
    zkService.setConfig(serviceConfig);
    
    List<ApiRole> apiRoles = new ArrayList<ApiRole>();
    for (String host : zkHosts) {
      ApiRole apiRole = new ApiRole();
      // Optional as of v6:
      // apiRole.setName();
      apiRole.setType("SERVER");
      LOG.debug("Adding ZooKeeper host " + host);
      apiRole.setHostRef(new ApiHostRef(host));
      apiRoles.add(apiRole);
    }
    zkService.setRoles(apiRoles);
    apiServices.add(zkService);
    servicesResource.createServices(apiServices);
    LOG.info("Added ZooKeeper service " + name);

    for (ApiRoleConfigGroup roleConfigGroup : servicesResource.getRoleConfigGroupsResource(name).readRoleConfigGroups()) {
      ApiConfigList roleConfigList = new ApiConfigList();
      Ini.Section roleConfigSection = 
        config.get(Constants.ZOOKEEPER_ROLE_CONFIG_SECTION);
      if (roleConfigSection != null && roleConfigSection.size() > 0) {
        for (Map.Entry<String, String> entry : roleConfigSection.entrySet()) {
          LOG.debug("Adding ZooKeeper role config key/value: " +
                    entry.getKey() + "=" + entry.getValue());
          roleConfigList.add(new ApiConfig(entry.getKey(), entry.getValue()));
        }
      }
      ApiRoleConfigGroup apiRoleConfigGroup = new ApiRoleConfigGroup();
      apiRoleConfigGroup.setConfig(roleConfigList);
      servicesResource.getRoleConfigGroupsResource(name).
        updateRoleConfigGroup(roleConfigGroup.getName(), 
                              apiRoleConfigGroup,
                              ("Updating ZooKeeper config for " +
                               roleConfigGroup.getName()));
    }
  }

  void validate() {
  }
}