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

public class SparkOnYarnService extends ClusterService {

  private static String SERVICE_TYPE="SPARK_ON_YARN";
  private enum RoleType { SPARK_YARN_HISTORY_SERVER, GATEWAY };
  private static final Logger LOG = Logger.getLogger(SparkOnYarnService.class);

  public SparkOnYarnService(Wini config, ServicesResourceV10 servicesResource) {

    super(config, servicesResource);
    setName(config.get(Constants.SPARK_CONFIG_SECTION, 
                       Constants.SPARK_SERVICE_NAME_PARAMETER));

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
      servicesResource.createServices(sparkServices);

      LOG.info("Spark on YARN services successfully created, now setting role configurations...");
  
      updateRoleConfigurations();
    }
  }

  public boolean preStartInitialization() {
    // Note the use of the firstRun() API call here, which will execute the
    // following commands:
    //   CreateSparkUserDirCommand
    //   CreateSparkHistoryDirCommand
    //   SparkUploadJarServiceCommand
    // Note that these commands can be run separately via the 
    // ServicesResource.serviceCommandByName() method, but using firstRun() is
    // a convenient shortcut. firstRun() also has the advantage of ensuring that
    // dependent services are started before executing these commands.
    LOG.info("Executing firstRun command for Spark");
    ApiCommand command = servicesResource.firstRun(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("firstRun command for Spark completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  public boolean postStartInitialization() {
    return true;
  }
}