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

/**
 * Base class for cluster services (HDFS, YARN, etc.). The general flow for
 * deploying a service is the following:
 * <p><ul>
 * <li> Create and populate the service-wide configuration.
 * <li> Create and populate role objects for each service role (e.g. for HDFS
 * the DataNode, NameNode, etc. roles).
 * <li> Use the above objects to create the service.
 * <li> Then iterate through each role and update the configuration parameters
 * associated with that role.
 * </ul></p>
 *
 * Valid service types as of CDH5: HDFS, MAPREDUCE, HBASE, OOZIE, ZOOKEEPER,
 * HUE, YARN, IMPALA, FLUME, HIVE, SOLR, SQOOP, KS_INDEXER, SQOOP_CLIENT, 
 * SENTRY, ACCUMULO16, KMS, SPARK_ON_YARN 
 */
public interface ClusterService {
  void deploy(Wini config, ServicesResourceV10 servicesResource);
}