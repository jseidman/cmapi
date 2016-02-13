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
package com.cloudera.cmapi.deploy;

import com.cloudera.api.DataView;
import com.cloudera.api.model.ApiCluster;
import com.cloudera.api.model.ApiClusterList;
import com.cloudera.api.model.ApiClusterVersion;
import com.cloudera.api.model.ApiHostRef;
import com.cloudera.api.model.ApiHostRefList;
import com.cloudera.api.model.ApiParcel;
import com.cloudera.api.v3.ParcelResource;
import com.cloudera.api.v10.RootResourceV10;

import com.cloudera.cmapi.deploy.services.ClusterService;
import com.cloudera.cmapi.deploy.services.ZooKeeperService;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import org.ini4j.Ini;
import org.ini4j.Wini;

/**
 * This class represents a CDH cluster managed by a Cloudera Manager server.
 * The Cluster class has responsibility for:
 * 
 *  Provisioning the cluster: setting cluster name, version, etc. and assigning
 *  hosts to the cluster:
 *  Provisioning required parcels across the cluster.
 *  Provisioning services (HDFS, YARN, etc.) on the cluster.
 */
public class Cluster {

  /**
   * Name of this cluster. Used in the CM UI.
   */
  private String name;

  /**
   * CDH version for this cluster.
   */
  private String version;

  /**
   * Hosts associated with this cluster.
   */
  private String[] clusterHosts;

  /**
   * Services (HDFS, YARN, etc. associated with this cluster.
   */
  private List<ClusterService> services;

  /**
   * Config parameters.
   */
  private Wini config;

  /**
   * 
   */
  private RootResourceV10 apiRoot;

  private static final Logger LOG = Logger.getLogger(Cluster.class);

  public Cluster(RootResourceV10 apiRoot, Wini config) {

    this.config = config;
    name = config.get("CLUSTER", Constants.CLUSTER_NAME_PARAMETER);
    version = config.get("CLUSTER", Constants.CLUSTER_CDH_VERSION_PARAMETER);
    clusterHosts = config.get("CLUSTER", Constants.CLUSTER_HOSTS_PARAMETER).split(",");
    this.apiRoot = apiRoot;
  }

  public String getName() {
    return name;
  }

  public void provisionCluster() {

    // Get list of any existing clusters:
    ApiClusterList clusters = apiRoot.getClustersResource()
      .readClusters(DataView.SUMMARY);
    // Then use that list to ensure a cluster doesn't already exist with 
    // the same name:
    boolean clusterExists = false;
    for (ApiCluster cluster : clusters) {
      if (cluster.getName().equals(name)) {
        clusterExists = true;
        break;
      }
    }

    if (clusterExists) {
      LOG.warn("Cluster with name " + name + " already exists, " +
                  "skipping provision cluster step");
    } else { 
      LOG.info("Creating cluster named " + name + " with CDH version " +
               version);
      // Create cluster object, set name and version, and add to collection of
      // clusters:
      clusters = new ApiClusterList();
      ApiCluster cluster = new ApiCluster();
      cluster.setName(name);
      cluster.setVersion(ApiClusterVersion.fromString(version));
      clusters.add(cluster);
      // Then call the command to create new cluster(s)
      // (com.cloudera.api.v*.ClustersResource.createClusters(ApiClusterList clusters)):
      apiRoot.getClustersResource().createClusters(clusters);

      // Create list of hosts for this cluster:
      List<ApiHostRef> apiHostRefs = new ArrayList();
      for (String hostname : clusterHosts) {
        apiHostRefs.add(new ApiHostRef(hostname));
      }

      apiHostRefs.add(new ApiHostRef(config.get("CM", Constants.CM_PRIVATE_HOSTNAME_PARAMETER)));

      // Then assign list of hosts to cluster
      // (com.cloudera.api.v*.ClustersResourceV*.ClustersResource.addHosts(String clusterName, ApiHostRefList hosts)):
      apiRoot.getClustersResource().addHosts(name,
                                             new ApiHostRefList(apiHostRefs));

      LOG.info("Successfully provisioned cluster");
    }
  }

  public void provisionParcels() {

    // Class that encapsulates default version of an artifact:
    DefaultArtifactVersion parcelVersion = null;

    // Get list of available parcels (com.cloudera.api.v*.ParcelsResource.readParcels()):
    for (ApiParcel parcel : apiRoot.getClustersResource().getParcelsResource(name).readParcels(DataView.FULL).getParcels()) {
      LOG.debug("Available parcels=" + parcel.getProduct() + ", " + parcel.getVersion());
      // Then find the greatest version of the CDH parcel, which is what we'll
      // use for deploying this cluster. An enhancement would be to allow
      // specifying the version to deploy, which will probably also require
      // configuring a specific parcel repo.
      if (parcel.getProduct().equals("CDH")
            && (parcelVersion == null || parcelVersion.compareTo(new DefaultArtifactVersion(parcel.getVersion())) < 0)) {
        LOG.info("Setting parcelVersion to " + parcel.getVersion());
        parcelVersion = new DefaultArtifactVersion(parcel.getVersion());
      }
    }

    LOG.info("Using parcel version " + parcelVersion.toString());

    // Get object encapsulating the CDH parcel 
    // (com.cloudera.api.v*.ParcelsResource.getParcelResource(String product, String version)):
    final ParcelResource parcelResource = apiRoot.getClustersResource()
      .getParcelsResource(name).getParcelResource("CDH", parcelVersion.toString());

    // Confirm this parcel isn't already activated on the cluster, then 
    // go through the steps to download, distribute, and activate:
    if (parcelResource.readParcel().getStage().equals("ACTIVATED")) {
      LOG.info("CDH parcel already activated, skipping parcel deploy steps...");
    } else {
      parcelResource.startDownloadCommand();
      while (!parcelResource.readParcel().getStage().equals("DOWNLOADED")) {
        LOG.debug("Waiting for CDH parcel to complete downloading");
        try {
          Thread.sleep(15000);
        } catch (InterruptedException e) {
        }
      }
      LOG.info("Completed download of CDH Parcel");

      parcelResource.startDistributionCommand();
      while (!parcelResource.readParcel().getStage().equals("DISTRIBUTED")) {
        LOG.debug("Waiting for CDH parcel to complete distribution");
        try {
          Thread.sleep(15000);
        } catch (InterruptedException e) {
        }
      }
      LOG.info("Completed distribution of CDH Parcel");

      parcelResource.activateCommand();
      while (!parcelResource.readParcel().getStage().equals("ACTIVATED")) {
        LOG.debug("Waiting for CDH parcel to complete activation");
        try {
          Thread.sleep(15000);
        } catch (InterruptedException e) {
        }
      }
      LOG.info("Completed activation of CDH Parcel");
    }
  }

  public void provisionServices() {
    ZooKeeperService zk = new ZooKeeperService();
    zk.deploy(config, apiRoot.getClustersResource().getServicesResource(name));
  }
}