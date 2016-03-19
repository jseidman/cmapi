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

import com.cloudera.api.v10.ServicesResourceV10;

import org.ini4j.Wini;

public class ClusterServiceFactory {

  private enum services { ZOOKEEPER, HDFS, YARN, HIVE, IMPALA, HBASE, OOZIE, SPARK_ON_YARN, KAFKA, HUE };

  public ClusterService getClusterService(String type, 
                                          Wini config,
                                          ServicesResourceV10 servicesResource) {

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

    return null;
  }
}