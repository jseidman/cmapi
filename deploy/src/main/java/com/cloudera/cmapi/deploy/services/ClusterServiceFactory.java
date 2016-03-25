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

import com.cloudera.api.v10.ServicesResourceV10;

import org.ini4j.Wini;

/**
 * Factory class to return cluster service objects.
 */
public class ClusterServiceFactory {

  /**
   * Valid cluster services.
   */
  private enum services { ZOOKEEPER, HDFS, YARN, HIVE, IMPALA, HBASE, OOZIE, SPARK_ON_YARN, KAFKA, HUE, SQOOP2, FLUME };

  /**
   * Construct and return the appropriate cluster object based on specified
   * service type.
   *
   * @param type Service type (HDFS, YARN, etc.).
   * @param config Object containing required config parameters.
   * @param servicesResource CM API object providing access to service
   * resources.
   *
   * @return Object encapsulating functionality to deploy a service.
   */
  public final ClusterService getClusterService(final String type,
                                                final Wini config,
                                                final ServicesResourceV10 servicesResource) {

    if (type == null) {
      return null;
    }

    if (services.ZOOKEEPER.name().equalsIgnoreCase(type)) {
      return new ZooKeeperService(config, servicesResource);
    }

    if (services.HDFS.name().equalsIgnoreCase(type)) {
      return new HDFSService(config, servicesResource);
    }

    if (services.YARN.name().equalsIgnoreCase(type)) {
      return new YARNService(config, servicesResource);
    }

    if (services.HIVE.name().equalsIgnoreCase(type)) {
      return new HiveService(config, servicesResource);
    }

    if (services.IMPALA.name().equalsIgnoreCase(type)) {
      return new ImpalaService(config, servicesResource);
    }

    if (services.OOZIE.name().equalsIgnoreCase(type)) {
      return new OozieService(config, servicesResource);
    }

    if (services.SPARK_ON_YARN.name().equalsIgnoreCase(type)) {
      return new SparkOnYarnService(config, servicesResource);
    }

    if (services.KAFKA.name().equalsIgnoreCase(type)) {
      return new KafkaService(config, servicesResource);
    }

    if (services.HUE.name().equalsIgnoreCase(type)) {
      return new HueService(config, servicesResource);
    }

    if (services.SQOOP2.name().equalsIgnoreCase(type)) {
      return new Sqoop2Service(config, servicesResource);
    }

    if (services.FLUME.name().equalsIgnoreCase(type)) {
      return new FlumeService(config, servicesResource);
    }

    return null;
  }
}
