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

# Set up the Cloudera Manager server and daemons. This should be run on the
# host on which the CM server is to run.

configfile=$1

if [ -z $configfile ]
then
    echo "usage: $0 config-file-name"
    echo "config-file-name should be the nane of a file containing configuration parameters for use by these scripts"
    exit 1
fi

source $configfile

# Prep Cloudera repo
sudo yum -y install wget
wget http://archive.cloudera.com/cm5/redhat/6/x86_64/cm/cloudera-manager.repo
sudo mv cloudera-manager.repo /etc/yum.repos.d/

# Turn off firewall
sudo service iptables stop
sudo chkconfig iptables off

# Turn off SELINUX
sudo chmod 666 /selinux/enforce; sudo echo 0 >/selinux/enforce; sudo chmod 644 /selinux/enforce 

# Install and enable NTP
sudo yum -y install ntp
sudo chkconfig ntpd on
sudo service ntpd start

# Install and start CM processes
sudo yum -y install oracle-j2sdk1.7 cloudera-manager-agent cloudera-manager-daemons cloudera-manager-server cloudera-manager-server-db-2
sudo service cloudera-scm-server-db start
sudo service cloudera-scm-server start
sudo sed -i.bak -e"s%server_host=localhost%server_host=$cmserver%" /etc/cloudera-scm-agent/config.ini
sudo service cloudera-scm-agent start