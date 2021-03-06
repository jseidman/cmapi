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

import com.cloudera.api.model.ApiBulkCommandList;
import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.model.ApiRole;
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

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

/**
 * Class to manage HDFS service deployment.
 */
public class HDFSService extends ClusterService {

  /**
   * Service type. This needs to match a valid CDH service type.
   */
  private static final String SERVICE_TYPE = "HDFS";

  /**
   * Name to assign to NameNode role.
   */
  private String namenodeRoleName;

  /**
   * Role types associated with this service.
   */
  private enum RoleType { DATANODE, NAMENODE, SECONDARYNAMENODE, BALANCER, GATEWAY, HTTPFS, FAILOVERCONTROLLER, JOURNALNODE, NFSGATEWAY };

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(HDFSService.class);

  /**
   * Constructor initializes required parameters to execute deployment.
   *
   * @param config Configuration parameters.
   * @param servicesResource Cloudera Manager API object providing access
   * to functionality for configuring, creating, etc. services on a cluster.
   */
  public HDFSService(final Wini config,
                     final ServicesResourceV10 servicesResource) {
    super(config, servicesResource);
    setName(config.get(Constants.HDFS_CONFIG_SECTION,
                       Constants.HDFS_SERVICE_NAME_PARAMETER));
    setServiceType(SERVICE_TYPE);
    namenodeRoleName = config.get(Constants.HDFS_CONFIG_SECTION,
                                  Constants.HDFS_NAMENODE_NAME_PARAMETER);
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
      ApiServiceList hdfsServices = new ApiServiceList();
      ApiService hdfsService = new ApiService();
      hdfsService.setType(SERVICE_TYPE);
      hdfsService.setName(name);

      // Set service configuration:
      Ini.Section serviceConfigSection =
        config.get(Constants.HDFS_SERVICE_CONFIG_SECTION);
      ApiServiceConfig serviceConfig =
        getServiceConfig(serviceConfigSection);
      hdfsService.setConfig(serviceConfig);

      // Create service roles:
      List<ApiRole> hdfsRoles = new ArrayList<ApiRole>();

      LOG.info("Adding NameNode role...");
      hdfsRoles.addAll(createRoles(RoleType.NAMENODE.name(), //null,
                                   RoleType.NAMENODE.name(),
                                   config.get(Constants.HDFS_CONFIG_SECTION,
                                              Constants.HDFS_NAMENODE_HOST_PARAMETER).split(",")));

      LOG.info("Adding Secondary NameNode role...");
      hdfsRoles.addAll(createRoles(RoleType.SECONDARYNAMENODE.name(), null,
                                   config.get(Constants.HDFS_CONFIG_SECTION,
                                              Constants.HDFS_SECONDARYNAMENODE_HOST_PARAMETER).split(",")));

      LOG.info("Adding DataNode roles...");
      hdfsRoles.addAll(createRoles(RoleType.DATANODE.name(), null,
                                   config.get(Constants.HDFS_CONFIG_SECTION,
                                              Constants.HDFS_DATANODE_HOSTS_PARAMETER).split(",")));

      LOG.info("Adding Gateway roles...");
      hdfsRoles.addAll(createRoles(RoleType.GATEWAY.name(), null,
                                   config.get(Constants.HDFS_CONFIG_SECTION,
                                              Constants.HDFS_GATEWAY_HOSTS_PARAMETER).split(",")));

      for (ApiRole role : hdfsRoles) {
        LOG.debug("role type=" + role.getType() + ", host=" + role.getHostRef());
      }

      hdfsService.setRoles(hdfsRoles);
      hdfsServices.add(hdfsService);
      // /api/v1/clusters/{clusterName}/services
      servicesResource.createServices(hdfsServices);

      LOG.info("HDFS services successfully created, now setting role " +
               "configurations...");

      updateRoleConfigurations();
    }
  }

  /**
   * Perform initialization tasks before starting the HDFS service. This
   * method will call the command to format HDFS.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean preStartInitialization() {
    boolean status = false;
    LOG.info("Formatting HDFS...");
    ApiRoleNameList roleNames =
      new ApiRoleNameList(new ArrayList<>(Arrays.asList(RoleType.NAMENODE.name())));
    // /clusters/{clusterName}/services/{serviceName}/roleCommands/hdfsFormat
    ApiBulkCommandList commands =
      servicesResource.getRoleCommandsResource(name).formatCommand(roleNames);
    for (ApiCommand command : commands) {
      status = CMServer.waitForCommand(command).booleanValue();
      if (status == false) {
        return status;
      }
    }
    LOG.info("Format HDFS command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  /**
   * Perform initialization tasks after HDFS service is started. This method
   * will create the HDFS temp directory.
   *
   * @return true if setup tasks complete successfully, false otherwise.
   */
  public final boolean postStartInitialization() {
    boolean status = false;
    LOG.info("Creating HDFS temp directory...");
    ApiCommand command = servicesResource.hdfsCreateTmpDir(name);
    status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Create HDFS temp directory completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }
}
