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

public class HDFSService implements ClusterService {

  private static String SERVICE_TYPE="HDFS";
  private enum RoleTypes { DATANODE, NAMENODE, SECONDARYNAMENODE, BALANCER, GATEWAY, HTTPFS, FAILOVERCONTROLLER, JOURNALNODE, NFSGATEWAY };
  private String name;
  private static final Logger LOG = Logger.getLogger(HDFSService.class);

  public void deploy(Wini config, ServicesResourceV10 servicesResource) {

    boolean provisionRequired = false;
    name = config.get(Constants.HDFS_CONFIG_SECTION, 
                      Constants.HDFS_SERVICE_NAME_PARAMETER);
    try {
      provisionRequired = 
        servicesResource.readService(name) == null;
    } catch (Exception e) {
      provisionRequired = true;
    }

    if (!provisionRequired) {
      LOG.info("HDFS services already deployed. Skipping...");
    } else {
      LOG.info("Deploying HDFS services...");
      ApiServiceList hdfsServices = new ApiServiceList();
      ApiService hdfsService = new ApiService();
      hdfsService.setType(SERVICE_TYPE);
      hdfsService.setName(name);

      ApiServiceConfig serviceConfig = new ApiServiceConfig();
      Ini.Section serviceConfigSection = 
        config.get(Constants.HDFS_SERVICE_CONFIG_SECTION);
      if (serviceConfigSection != null && serviceConfigSection.size() > 0) {
        for (Map.Entry<String, String> entry : serviceConfigSection.entrySet()) {
          LOG.debug("Adding HDFS service config key/value: " +
                    entry.getKey() + "=" + entry.getValue());
          serviceConfig.add(new ApiConfig(entry.getKey(), entry.getValue()));
        }
      }
      hdfsService.setConfig(serviceConfig);
      
      List<ApiRole> hdfsRoles = new ArrayList<ApiRole>();
      
      LOG.info("Adding NameNode role...");
      ApiRole nnRole = new ApiRole();
      // Optional as of v6:
      // nnRole.setName("NAMENODE");
      nnRole.setType("NAMENODE");
      nnRole.setHostRef(new ApiHostRef(config.get(Constants.HDFS_CONFIG_SECTION, 
                                                  Constants.HDFS_NAMENODE_HOST_PARAMETER)));
      hdfsRoles.add(nnRole);

      LOG.info("Adding Secondary NameNode role...");
      ApiRole snnRole = new ApiRole();
      // Optional as of v6:
      // nnRole.setName("SECONDARYNAMENODE");
      snnRole.setType("SECONDARYNAMENODE");
      snnRole.setHostRef(new ApiHostRef(config.get(Constants.HDFS_CONFIG_SECTION, 
                                                  Constants.HDFS_SECONDARYNAMENODE_HOST_PARAMETER)));
      hdfsRoles.add(snnRole);

      LOG.info("Adding DataNode roles...");
      String[] dnHosts = 
        config.get(Constants.HDFS_CONFIG_SECTION, Constants.HDFS_DATANODE_HOSTS_PARAMETER).split(",");

      for (String host : dnHosts) {
        ApiRole apiRole = new ApiRole();
        // Optional as of v6:
        // apiRole.setName();
        apiRole.setType("DATANODE");
        LOG.debug("Adding DataNode host " + host);
        apiRole.setHostRef(new ApiHostRef(host));
        hdfsRoles.add(apiRole);
      }

      LOG.info("Adding Gateway roles...");
      String[] gwHosts = 
        config.get(Constants.HDFS_CONFIG_SECTION, Constants.HDFS_GATEWAY_HOSTS_PARAMETER).split(",");

      for (String host : gwHosts) {
        ApiRole apiRole = new ApiRole();
        // Optional as of v6:
        // apiRole.setName();
        apiRole.setType("GATEWAY");
        LOG.debug("Adding HDFS Gateway host " + host);
        apiRole.setHostRef(new ApiHostRef(host));
        hdfsRoles.add(apiRole);
      }

      for (ApiRole role : hdfsRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }
      
      hdfsService.setRoles(hdfsRoles);
      hdfsServices.add(hdfsService);
      servicesResource.createServices(hdfsServices);
      LOG.info("Created HDFS services");
  
      String roleType = null;
      for (ApiRoleConfigGroup roleConfigGroup : servicesResource.getRoleConfigGroupsResource(name).readRoleConfigGroups()) {
        roleType = roleConfigGroup.getRoleType();
        LOG.info("Looking for configuration params for role type=" + roleType);
        ApiConfigList roleConfigList = new ApiConfigList();
        Ini.Section roleConfigSection = config.get(roleType);
        if (roleConfigSection != null && roleConfigSection.size() > 0) {
          LOG.info("Found configuration params for role type=" + roleType);
          for (Map.Entry<String, String> entry : roleConfigSection.entrySet()) {
            LOG.debug("Role type=" + roleType +
                      ", adding config key/value: " +
                      entry.getKey() + "=" + entry.getValue());
            roleConfigList.add(new ApiConfig(entry.getKey(), entry.getValue()));
          }
        }
        ApiRoleConfigGroup apiRoleConfigGroup = new ApiRoleConfigGroup();
        apiRoleConfigGroup.setConfig(roleConfigList);
        servicesResource.getRoleConfigGroupsResource(name).
          updateRoleConfigGroup(roleConfigGroup.getName(), 
                                apiRoleConfigGroup,
                                ("Updating HDFS role config for " +
                                 roleConfigGroup.getName()));
      }
    }
  }
}