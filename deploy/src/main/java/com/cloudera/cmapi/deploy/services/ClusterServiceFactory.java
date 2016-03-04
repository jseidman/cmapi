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

public class ClusterServiceFactory {

  private enum services { ZOOKEEPER, HDFS, YARN, HIVE, IMPALA, HBASE };

  public ClusterService getClusterService(String type) {

    if (type == null) {
      return null;
    }

    if (services.ZOOKEEPER.name().equalsIgnoreCase(type)) {
      return new ZooKeeperService();
    }

    if (services.HDFS.name().equalsIgnoreCase(type)) {
      return new HDFSService();
    }

    if (services.YARN.name().equalsIgnoreCase(type)) {
      return new YARNService();
    }

    if (services.HIVE.name().equalsIgnoreCase(type)) {
      return new HiveService();
    }

    if (services.IMPALA.name().equalsIgnoreCase(type)) {
      return new ImpalaService();
    }

    return null;
  }
}