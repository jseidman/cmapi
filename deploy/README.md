CDH Deployment via the Cloudera Manager API Java Client
=======================================================

Java application to deploy a Cloudera cluster using the Java client to the Cloudera Manager API. Given provisioned instances, this application will execute all steps to deploy and start a working cluster. These steps include:

* Deploying the Cloudera management services.
* Initializing the cluster.
* Deploying a configured set of services to the cluster.
* Perform required initialization steps and start the cluster.

As noted above, this application requires provisioned instances for deployment. This includes a supported OS with required changes (e.g. disabled SELinux, ntpd running, etc.), as well as the Cloudera Manager Server and Agents installed. A set of scripts to perform these tasks on EC2 are provided. Instructions on using these scripts are below.

This is intended as an example of using the Java API client to perform Cloudera cluster installations, and has only been tested to deploy test clusters. However, deploying production clusters should mainly require providing more extensive parameters in the configuration file. Details on updating the configuration file are in the instructions below.

A set of TODOs is below, but some limitations include:

* Not all available services are implemented. See the TODOs for a list of missing services.
* Enabling HA is not yet implemented.
* This currently will install the latest CDH5 version. An option to specify the version is not yet implemented.

This application and associated scripts have been tested on EC2, but should not be hard to adapt to work on other environments.

Files
=====

The following describes the artifacts in this repository:

* **scripts/:** A set of scripts and configuration files to prepare a set of instances for the Cloudera deployment. This also includes scripts for deploying a test MySQL instance for use as the Hive metastore/Oozie DB, as well as a script to completely uninstall the Cloudera components just in case things go horribly wrong and you want to start over with clean instances. Instructions on using these scripts are below.
* **src/main/java:** Java classes implementing the deployment application. More details below.
* **src/main/resources/cmdeploy.ini**: Configuration parameters for the Cloudera deployment. Also detailed in the instructions below.

Usage Instructions
==================

More details are provided below for the process required to deploy a cluster via the CM API, but the following are the steps to test this application using EC2. Note that the scripts in this repo are adapted from the scripts provided as part of the [Python API example](https://github.com/cloudera/cm_api/tree/master/python/examples/auto-deploy).

* Deploy instances on Amazon EC2 using a supported OS. Testing was done with the following:
  * RHEL 6.5, ami-7df0bd4d (us-west-2c).
  * Instance type: m3.2xlarge
* Update **scripts/hosts.txt** with cluster node hostnames, excluding the instance that will host the Cloudera Manager server -- this hostname will be updated in cmdeploy.cfg. Hostnames entered in this file should be the public hostnames.
* Update **scripts/cmdeploy.cfg:**
  * Set **cmserver** to the public hostname of the instance to host the Cloudera Manager server. Note that this host can also be used for master services such as the NameNode and Resource Manager.
  * Set **pemfile** to point to a valid AWS PEM file.
  * Set **user** to a valid user for the AWS instances. For RHEL/CentOS this is usually ec2-user.
* Execute script to set up instances: **cd scripts; ./setup-aws-hosts.sh cmdeploy.cfg**. This will perform the following steps on the EC2 instances:
  * Install the Cloudera repo files on the hosts.
  * Disable iptables.
  * Disable SELinux
  * Enable ntpd.
  * Install the Oracle JDK, Cloudera Manager server and agents, and the Cloudera Manager database instance.
  * Start the CM server and agent processes.
* If necessary, update the **cdh_archive_url, hive_version, and schema_file** parameters in the **install_mysql.sh** script.
* Then execute script to set up a MySQL instance to host the Hive metastore and Oozie DBs: **./run_install_mysql.sh cmdeploy.cfg**.

After the above completes, update the cluster configuration and then execute the deployment application:

* Update **src/main/resources/cmdeploy.ini**. Comments are provided for the values in this file, but some key parameters to note for updating:
  * Make sure hostname parameters are updated, such as the Cloudera Manager host, service hosts, etc.
  * Update the **services** parameter in the **[CLUSTER]** section based on the services that should be deployed.
  * Update the database parameters for the management services, including the host, and username/password.
  * Update service and role configurations as necessary.

Before executing the deployment application, a couple of things to note:

* The code currently doesn't enable a CM license, so before executing log into the CM UI and execute the trial license.
* To help troubleshoot, set the "Enable Debugging of API" parameter in the Administration settings in the CM UI. See [Debugging Tips](http://cloudera.github.io/cm_api/docs/debugging-tips/).

Finally, build and execute the deployment application:

* **cd deploy**
* **mvn clean package**
* **mvn exec:java -Dcmapi.ini.file=cmdeploy.ini -Dexec.mainClass="com.cloudera.cmapi.deploy.CMApiDeploy"**

If for some reason you want to start over with clean instances, use the **uninstall.sh** scripts in the scripts directory.


Adding a service:

* Add configuration parameters to resources/cmdeploy.ini. See that file for
 details.
** Add service to services parameter in [CLUSTER] section
** Add configuration sections for service
* Add corresponding variables to com.cloudera.cmapi.deploy.Constants.
* Add a new class in com.cloudera.cmapi.deploy.services implementing ClusterService.
* Update com.cloudera.cmapi.deploy.services.ClusterServiceFactory
* Update cmdeploy.ini by adding new service to "services" parameter in the "[CLUSTER]" section. 


TODOS:
======

**General:**
* Add HA support, including HDFS and YARN.
* Add ability to specify the CDH version to deploy.
* Add additional error checking and validation to code.
* Consider making CMServer class a singleton.
* Add option to enable Kerberos.
* Explore options to enable encryption.
* Add additional services:
  * Navigator
  * Kudu
  * HBase
  * Accumulo
  * Search
  * Others?

**Specific classes:**
* com.cloudera.cmapi.deploy.services.KafkaService:
  * Make updates to enable deployment of MirrorMaker role. For now the relevant code is commented out.
* com.cloudera.cmapi.deploy.services.ManagementService
  * Class shares code with com.cloudera.cmapi.deploy.services.ClusterService. Consider refactoring, for example create a new Service base class and move the common code into that class.
  * Currently the deploy method checks for a license, but will proceed if license doesn't exist, which leads to an error. Code should be updated to add option to deploy trial license as well as possibly provide option to upload a license. In the case where no license option exists, the code should throw an exception instead of proceeding.
