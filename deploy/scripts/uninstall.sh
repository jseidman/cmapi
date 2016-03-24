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

# Uninstall CM and CDH bits from hosts.
#
# Before running:
#  Stop cluster and management services via CM UI.
#  Deactivate and remove any Parcels.
#
# See http://www.cloudera.com/documentation/enterprise/latest/topics/cm_ig_uninstall_cm.html
#
# After running, remove external DBs, for example:
#  * sudo yum remove -y mysql mysql-server
#  * sudo mv /var/lib/mysql /var/lib/mysql.old

configfile=$1

if [ -z $configfile ]
then
    echo "usage: $0 config-file-name"
    echo "config-file-name should be the name of a file containing configuration parameters for use by these scripts"
    exit 1
fi

source $configfile

# Remove CM server:
ssh -t -i $pemfile $user@$cmserver "sudo service cloudera-scm-server stop;sudo service cloudera-scm-server-db stop;sudo yum -y remove cloudera-manager-server;sudo yum remove -y cloudera-manager-server-db-2"

# Remove CM agents and managed software:
ssh -t -i $pemfile $user@$cmserver "sudo service cloudera-scm-agent hard_stop;sudo yum remove -y 'cloudera-manager-*';sudo yum clean all "

for host in `cat $workerhostsfile`
do
    ssh -t -i $pemfile $user@$host "sudo yum -y remove 'cloudera-manager-*';sudo yum clean all"
done

# Remove CM and user data:
ssh -t -i $pemfile $user@$cmserver "for u in cloudera-scm flume hadoop hdfs hbase hive httpfs hue impala llama mapred oozie solr spark sqoop sqoop2 yarn zookeeper; do sudo kill $(ps -u $u -o pid=); done"
ssh -t -i $pemfile $user@$cmserver "sudo umount cm_processes;sudo rm -Rf /usr/share/cmf /var/lib/cloudera* /var/cache/yum/cloudera* /var/log/cloudera* /var/run/cloudera*;sudo rm /tmp/.scm_prepare_node.lock"
ssh -t -i $pemfile $user@$cmserver "sudo rm -Rf /var/lib/flume-ng /var/lib/hadoop* /var/lib/hue /var/lib/navigator /var/lib/oozie /var/lib/solr /var/lib/sqoop* /var/lib/zookeeper /etc/cloudera-scm-agent /etc/cloudera-scm-server"
ssh -t -i $pemfile $user@$cmserver "sudo rm -Rf /data/dfs /data/yarn"

for host in `cat $workerhostsfile`
do
    ssh -t -i $pemfile $user@$host "for u in cloudera-scm flume hadoop hdfs hbase hive httpfs hue impala llama mapred oozie solr spark sqoop sqoop2 yarn zookeeper; do sudo kill $(ps -u $u -o pid=); done"
    ssh -t -i $pemfile $user@$host "sudo umount cm_processes;sudo rm -Rf /usr/share/cmf /var/lib/cloudera* /var/cache/yum/cloudera* /var/log/cloudera* /var/run/cloudera*;sudo rm /tmp/.scm_prepare_node.lock"
    ssh -t -i $pemfile $user@$host "sudo rm -Rf /var/lib/flume-ng /var/lib/hadoop* /var/lib/hue /var/lib/navigator /var/lib/oozie /var/lib/solr /var/lib/sqoop* /var/lib/zookeeper /var/local/kafka/"
    ssh -t -i $pemfile $user@$host "sudo rm -Rf /data/dfs /data/yarn"
done


