#!/bin/bash
# Licensed to Cloudera, Inc. under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  Cloudera, Inc. licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Install MySQL and create Hive metastore objects and Oozie DB. Note that these
# steps create a non-secure MySQL install and are for test purposes only.
# This script also assumes the Hive Metastore Server and Ooozie Server  will run
# on the same host as MySQL. If this isn't the case modify the create user
# commands accordingly. 

# The following should point to the Hive distribution that matches the version
# of CDH/Hive that will be installed on the cluster:
export cdh_archive_url=http://archive.cloudera.com/cdh5/cdh/5
export hive_version=hive-1.1.0-cdh5.6.0
export schema_file=hive-schema-1.1.0.mysql.sql

sudo yum -y install mysql-server
sudo service mysqld start
sudo /sbin/chkconfig mysqld on

wget ${cdh_archive_url}/${hive_version}.tar.gz
tar -xvf ${hive_version}.tar.gz
cd ${hive_version}/scripts/metastore/upgrade/mysql/
mysql -uroot --execute="CREATE DATABASE metastore; USE metastore; SOURCE $schema_file;"
mysql -uroot --execute="CREATE USER 'hiveuser'@'`hostname -f`' IDENTIFIED BY 'hivepass';"
mysql -uroot --execute="REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hiveuser'@'`hostname -f`';"
mysql -uroot --execute="GRANT SELECT,INSERT,UPDATE,DELETE,LOCK TABLES,EXECUTE ON metastore.* TO 'hiveuser'@'`hostname -f`';"
mysql -uroot --execute="CREATE USER 'hiveuser'@'localhost' IDENTIFIED BY 'hivepass';"
mysql -uroot --execute="REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hiveuser'@'localhost';"
mysql -uroot --execute="GRANT SELECT,INSERT,UPDATE,DELETE,LOCK TABLES,EXECUTE ON metastore.* TO 'hiveuser'@'localhost';"
mysql -uroot --execute="CREATE DATABASE oozie;"
mysql -uroot --execute="CREATE USER 'oozieuser'@'`hostname -f`' IDENTIFIED BY 'ooziepass';"
mysql -uroot --execute="CREATE USER 'oozieuser'@'%' IDENTIFIED BY 'ooziepass';"
mysql -uroot --execute="CREATE USER 'oozieuser'@'localhost' IDENTIFIED BY 'ooziepass';"
mysql -uroot --execute="GRANT ALL PRIVILEGES ON oozie.* TO 'oozieuser'@'`hostname -f`'"
mysql -uroot --execute="GRANT ALL PRIVILEGES ON oozie.* TO 'oozieuser'@'%';"
mysql -uroot --execute="GRANT ALL PRIVILEGES ON oozie.* TO 'oozieuser'@'localhost';"
mysql -uroot --execute="FLUSH PRIVILEGES;"

# Make the mysql driver available to hive
sudo yum -y install mysql-connector-java
sudo mkdir -p /usr/lib/hive/lib/
sudo ln -s /usr/share/java/mysql-connector-java.jar /usr/lib/hive/lib/mysql-connector-java.jar

