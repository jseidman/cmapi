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

import com.cloudera.api.DataView;

import com.cloudera.api.model.ApiConfig;
import com.cloudera.api.model.ApiHostRef;
import com.cloudera.api.model.ApiRole;
import com.cloudera.api.model.ApiRoleConfigGroup;
import com.cloudera.api.model.ApiService;
import com.cloudera.api.model.ApiServiceConfig;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v8.ClouderaManagerResourceV8;
import com.cloudera.api.v8.MgmtServiceResourceV8;

import com.cloudera.cmapi.deploy.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.ini4j.Ini;
import org.ini4j.Wini;

public class ManagementService {

  private enum ValidRoleTypes { HOSTMONITOR, SERVICEMONITOR, ACTIVITYMONITOR,
      REPORTSMANAGER, EVENTSERVER, ALERTPUBLISHER, NAVIGATOR };

  //  private String[] deployRoleTypes;

  private static final Logger LOG = Logger.getLogger(ManagementService.class);

  public void deploy(Wini config, ClouderaManagerResourceV8 cmResource) {

    final String[] deployRoleTypes = config.get("MGMT_SERVICE", Constants.CM_MANAGEMENT_ROLETYPES_PARAMETER).split(",");
    LOG.info("Validating configuration parameters for management service...");
    validateRoleTypes(deployRoleTypes);
    LOG.info("Successfully validated configuration parameters for management service");

    // Confirm that management services aren't already set up. Note that
    // setting up Cloudera Management Services requires a valid license:
    boolean cmsProvisionRequired = false;
    if (cmResource.readLicense() != null) {
      try {
        // com.cloudera.api.v*.MgmtServiceResource.readService()
        cmsProvisionRequired = 
          cmResource.getMgmtServiceResource().readService(DataView.SUMMARY) == null;
      } catch (Exception e) {
        cmsProvisionRequired = true;
      }
    }
  
    if (!cmsProvisionRequired) {
      LOG.info("Management services already available, skipping provisioning.");
    } else {
      LOG.info("Provisioning management services...");
      String cmhost = config.get("CM", Constants.CM_PRIVATE_HOSTNAME_PARAMETER);
      final ApiHostRef cmHostRef = new ApiHostRef(cmhost);
      ApiService cmService = new ApiService();
      List<ApiRole> cmRoles = new ArrayList<ApiRole>();
        
      // Create the managment service and add the roles specified by the config:
      cmService.setName(config.get("MGMT_SERVICE", Constants.CM_MGMT_SERVICE_NAME_PARAMETER));
      cmService.setType("MGMT");
      for (String type : deployRoleTypes) {
        ApiRole role = new ApiRole();
        role.setName(type + "-1");
        role.setType(type);
        role.setHostRef(cmHostRef);
        cmRoles.add(role);
      }
      cmService.setRoles(cmRoles);
      // com.cloudera.api.v*.MgmtServiceResource.setupCMS(ApiService service)
      cmResource.getMgmtServiceResource().setupCMS(cmService);
    }

    // Loop through each role config group in the Cloudera Management Services:
    for (ApiRoleConfigGroup roleConfigGroup : 
           // com.cloudera.api.v*.MgmtRoleConfigGroupsResource.readRoleConfigGroups()
           cmResource.getMgmtServiceResource().getRoleConfigGroupsResource().readRoleConfigGroups()) {

      // Fetch config parameters for each service, and add to role group
      // object:
      ApiRoleConfigGroup newRoleConfigGroup = new ApiRoleConfigGroup();
      ApiServiceConfig serviceConfig = new ApiServiceConfig();
      Ini.Section section = config.get(roleConfigGroup.getRoleType());
      if (section != null && section.size() > 0) {
        LOG.debug("role type=" + roleConfigGroup.getRoleType() +
                  " section size=" + section.size());
        for (Map.Entry<String, String> entry : section.entrySet()) {
          serviceConfig.add(new ApiConfig(entry.getKey(), entry.getValue()));
        }
      }
      newRoleConfigGroup.setConfig(serviceConfig);
      
      // Then update management service configs on server:
      // (com.cloudera.api.v*.RoleConfigGroupsResource.updateRoleConfigGroup())
      cmResource.getMgmtServiceResource()
        .getRoleConfigGroupsResource()
        .updateRoleConfigGroup(roleConfigGroup.getName(), newRoleConfigGroup,
                               ("Updating Management Services config for " +
                                roleConfigGroup.getName()));
    }
  }

  /**
   * Validate whether a list of role types to be deployed match valid role 
   * types for this service.
   *
   * @param roletypes enum of role types to be deployed.
   *
   * @return true if input roles match valid roles, false otherwise.
   */ 
  public boolean validateRoleTypes(String[] roletypes) {

    for (String type : roletypes) {
      if (!isValidRoleType(type)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Ensure a role type matches one of the valid role types for this service.
   *
   * @param type The role type to check.
   *
   * @return true if input role matches a valid role, false otherwise.
   */
  public boolean isValidRoleType(String type) {
    for (ValidRoleTypes rt : ValidRoleTypes.values()) {
      if (rt.name().equals(type)) {
        return true;
      }
    }

    return false;
  }
}