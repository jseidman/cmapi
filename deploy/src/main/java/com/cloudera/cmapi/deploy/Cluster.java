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
package com.cloudera.cmapi.deploy;

import com.cloudera.api.DataView;
import com.cloudera.api.model.ApiCluster;
import com.cloudera.api.model.ApiClusterList;
import com.cloudera.api.model.ApiClusterVersion;
import com.cloudera.api.model.ApiCommand;
import com.cloudera.api.model.ApiHostRef;
import com.cloudera.api.model.ApiHostRefList;
import com.cloudera.api.model.ApiParcel;
import com.cloudera.api.v10.RootResourceV10;
import com.cloudera.api.v10.ServicesResourceV10;
import com.cloudera.api.v3.ParcelResource;

import com.cloudera.cmapi.deploy.services.ClusterService;
import com.cloudera.cmapi.deploy.services.ClusterServiceFactory;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import org.ini4j.Wini;

/**
 * This class represents a CDH cluster managed by a Cloudera Manager server.
 * The Cluster class has responsibility for:
 * <p><ul>
 * <li>Provisioning the cluster: setting cluster name, version, etc.
 * and assigning hosts to the cluster.
 * <li>Provisioning required parcels across the cluster.
 * <li>Provisioning services (HDFS, YARN, etc.) on the cluster.
 * </ul><p>
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
   * Configuration parameters.
   */
  private Wini config;

  /**
   * Top level resource object providing access to the CM API namespace.
   */
  private RootResourceV10 apiRoot;

  /**
   * List of services that should be part of this cluster.
   */
  private String[] servicesToDeploy;

  /**
   * Valid products/Parcels.
   */
  public static enum PRODUCT { CDH, KAFKA };

  /**
   * Log4j logger.
   */
  private static final Logger LOG = Logger.getLogger(Cluster.class);

  /**
   * Sleep time to wait for commands to complete execution.
   */
  private static final long SLEEP_LENGTH = 10000;

  /**
   * Constructor initializes cluster parameters including the cluster name and
   * version, initializes list of hosts to be assigned to cluster, and
   * initializes list of services to be deployed as part of this cluster.
   *
   * @param config Object containing required config parameters.
   * @param apiRoot Object providing access to the CM API
   * root namespace.
   */
  public Cluster(final RootResourceV10 apiRoot, final Wini config) {

    this.config = config;
    name = config.get(Constants.CLUSTER_CONFIG_SECTION,
                      Constants.CLUSTER_NAME_PARAMETER);
    version = config.get(Constants.CLUSTER_CONFIG_SECTION,
                         Constants.CLUSTER_CDH_VERSION_PARAMETER);
    clusterHosts =
      config.get(Constants.CLUSTER_CONFIG_SECTION,
                 Constants.CLUSTER_HOSTS_PARAMETER).split(",");
    this.apiRoot = apiRoot;
    servicesToDeploy =
      config.get(Constants.CLUSTER_CONFIG_SECTION,
                 Constants.CLUSTER_SERVICES_PARAMETER).split(",");
  }

  /**
   * Get cluster name.
   *
   * @return Name assigned to this cluster
   */
  public final String getName() {
    return name;
  }

  /**
   * Perform required tasks to provision a cluster managed by Cloudera Manager.
   * This includes tasks like setting the cluster name and version and
   * assigning hosts to the cluster.
   */
  public final void provisionCluster() {

    // Get list of any existing clusters and ensure this cluster doesn'
    // already exist:
    ApiClusterList clusters = apiRoot.getClustersResource()
      .readClusters(DataView.SUMMARY);
    boolean clusterExists = false;
    for (ApiCluster cluster : clusters) {
      if (cluster.getName().equals(name)) {
        clusterExists = true;
        break;
      }
    }

    // If this is a new cluster, proceed with tasks to provision, otherwise
    // return:
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
      // /api/v1/clusters
      apiRoot.getClustersResource().createClusters(clusters);

      // Create list of hosts for this cluster:
      List<ApiHostRef> apiHostRefs = new ArrayList();
      for (String hostname : clusterHosts) {
        apiHostRefs.add(new ApiHostRef(hostname));
      }

      apiHostRefs.add(new ApiHostRef(config.get("CM", Constants.CM_PRIVATE_HOSTNAME_PARAMETER)));

      // Then assign list of hosts to cluster
      //  /api/v3/clusters/{clusterName}/hosts
      apiRoot.getClustersResource().addHosts(name,
                                             new ApiHostRefList(apiHostRefs));

      LOG.info("Successfully provisioned cluster");
    }
  }

  /**
   * Execute steps to download, distribute, and activate Parcels required
   * for deploying this cluster.
   */
  public final void provisionParcels() {
    // The CDH Parcel is required for deploying basic services to the cluster:
    provisionParcels(PRODUCT.CDH);
    // Kafka requires a separate Parcel:
    for (String service : servicesToDeploy) {
      if (service.equalsIgnoreCase(PRODUCT.KAFKA.name())) {
        provisionParcels(PRODUCT.KAFKA);
      }
    }
  }

  /**
   * Execute steps to download, distribute, and activate a specific Parcel
   * required for deploying this cluster.
   *
   * @param product Specific product Parcel (e.g. CDH, Kafka, etc.) to be
   * provisioned.
   */
  public final void provisionParcels(final PRODUCT product) {

    // Class that encapsulates default version of an artifact:
    DefaultArtifactVersion parcelVersion = null;

    // Get list of available parcels:
    //  /api/v3/clusters/{clusterName}/parcels
    for (ApiParcel parcel : apiRoot.getClustersResource().getParcelsResource(name).readParcels(DataView.FULL).getParcels()) {
      LOG.debug("Available parcels=" + parcel.getProduct() + ", " +
                parcel.getVersion());
      // Then find the greatest version of the parcel, which is what we'll
      // use for deploying this cluster. An enhancement would be to allow
      // specifying the version to deploy, which will probably also require
      // configuring a specific parcel repo.
      if (parcel.getProduct().equals(product.name())
            && (parcelVersion == null ||
                parcelVersion.compareTo(new DefaultArtifactVersion(parcel.getVersion())) < 0)) {
        LOG.info("Setting parcelVersion to " + parcel.getVersion());
        parcelVersion = new DefaultArtifactVersion(parcel.getVersion());
      }
    }

    LOG.info("Using parcel version " + parcelVersion.toString());

    // Get object encapsulating the parcel:
    // /api/v3/clusters/{clusterName}/parcels/products/{product}/versions/{version}
    final ParcelResource parcelResource = apiRoot.getClustersResource().getParcelsResource(name).getParcelResource(product.name(), parcelVersion.toString());

    // Confirm this parcel isn't already activated on the cluster, then
    // go through the steps to download, distribute, and activate:
    if (parcelResource.readParcel().getStage().equals("ACTIVATED")) {
      LOG.info(product +
               " parcel already activated, skipping parcel deploy steps...");
    } else {
      // /api/v3/clusters/{clusterName}/parcels/products/{product}/versions/{version}/commands/startDownload
      parcelResource.startDownloadCommand();
      while (!parcelResource.readParcel().getStage().equals("DOWNLOADED")) {
        LOG.info("Waiting for " + product + " parcel to complete downloading");
        try {
          Thread.sleep(SLEEP_LENGTH);
        } catch (InterruptedException e) {
          LOG.warn(e.getMessage());
        }
      }
      LOG.info("Completed download of " + product + " parcel");

      // /api/v3/clusters/{clusterName}/parcels/products/{product}/versions/{version}/commands/startDistribution
      parcelResource.startDistributionCommand();
      while (!parcelResource.readParcel().getStage().equals("DISTRIBUTED")) {
        LOG.info("Waiting for " + product + " parcel to complete distribution");
        try {
          Thread.sleep(SLEEP_LENGTH);
        } catch (InterruptedException e) {
          LOG.warn(e.getMessage());
        }
      }
      LOG.info("Completed distribution of " + product + " parcel");

      // /api/v3/clusters/{clusterName}/parcels/products/{product}/versions/{version}/commands/activate
      parcelResource.activateCommand();
      while (!parcelResource.readParcel().getStage().equals("ACTIVATED")) {
        LOG.info("Waiting for " + product + " parcel to complete activation");
        try {
          Thread.sleep(SLEEP_LENGTH);
        } catch (InterruptedException e) {
          LOG.warn(e.getMessage());
        }
      }
      LOG.info("Completed activation of " + product + " parcel");
    }
  }

  /**
   * Deploy required services (HDFS, YARN, etc.) to the cluster.
   */
  public final void provisionServices() {
    ServicesResourceV10 servicesResource =
      apiRoot.getClustersResource().getServicesResource(name);
    ClusterServiceFactory factory = new ClusterServiceFactory();
    // For each service to be deployed to cluster, get the corresponding
    // object representing that service and execute deployment:
    for (String service : servicesToDeploy) {
      ClusterService clusterService =
        factory.getClusterService(service, config, servicesResource);
      if (clusterService != null) {
        LOG.info("Deploying " + service + " service for cluster " + name);
        clusterService.deploy();
      } else {
        LOG.warn("No class found to deploy service: " + service);
      }
    }
  }

  /**
   * For each deployed service, perform any steps required before starting
   * cluster.
   */
  public final void preInitializeServices() {
    ServicesResourceV10 servicesResource =
      apiRoot.getClustersResource().getServicesResource(name);
    ClusterServiceFactory factory = new ClusterServiceFactory();
    for (String service : servicesToDeploy) {
      ClusterService clusterService =
        factory.getClusterService(service, config, servicesResource);
      if (clusterService != null) {
        LOG.info("Running pre-init for " + service + " service for cluster " + name);
        clusterService.preStartInitialization();
      }
    }
  }

  /**
   * For each deployed service, perform any steps required before starting
   * cluster.
   */
  public final void postInitializeServices() {
    ServicesResourceV10 servicesResource =
      apiRoot.getClustersResource().getServicesResource(name);
    ClusterServiceFactory factory = new ClusterServiceFactory();
    for (String service : servicesToDeploy) {
      ClusterService clusterService =
        factory.getClusterService(service, config, servicesResource);
      if (clusterService != null) {
        LOG.info("Running post-init for " + service + " service for cluster " + name);
        clusterService.postStartInitialization();
      }
    }
  }

  /**
   * Start cluster services. Note the use of the firstRun() call, added with
   * version 7 of the API. firstRun() will perform initialization tasks in
   * addition to starting services. Calling firstRun() removes need for the
   * preInitializeServices() and postInitializeServices() methods and
   * simplifies cluster startup by allowing Cloudera Manager to manage these
   * tasks. If for some reason it's preferred to manually manage these tasks
   * then comment out the call to firstRun() and un-comment the call to
   * startCommand().
   *
   * @return flag indicating success or failure of startup.
   */
  public final boolean startCluster() {
    // /api/v1/clusters/{clusterName}/commands/start
    //ApiCommand command = apiRoot.getClustersResource().startCommand(name);
    // /api/v7/clusters/{clusterName}/commands/firstRun
    ApiCommand command = apiRoot.getClustersResource().firstRun(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Start cluster command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }

  /**
   * Execute command to deploy client configurations.
   *
   * @return flag indicating success or failure of startup.
   */
  public final boolean deployClientConfigs() {
    // /api/v2/clusters/{clusterName}/commands/deployClientConfig
    ApiCommand command = apiRoot.getClustersResource().deployClientConfig(name);
    boolean status = CMServer.waitForCommand(command).booleanValue();
    LOG.info("Deploy client config command completed " +
             (status ? "successfully" : "unsuccessfully"));
    return status;
  }
}
