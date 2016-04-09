CDH Deployment via the Cloudera Manager API Java Client
=======================================================

Example Java application to deploy a Cloudera cluster using the Java client to the Cloudera Manager API. Given provisioned instances, this application will execute all steps to deploy and start a working cluster. These steps include:

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

* The code currently doesn't enable a CM license, so before executing log into the CM UI and execute the trial license (or upload a valid license).
* To help troubleshoot, set the "Enable Debugging of API" parameter in the Administration settings in the CM UI. See [Debugging Tips](http://cloudera.github.io/cm_api/docs/debugging-tips/).

Finally, build and execute the deployment application:

* **cd deploy**
* **mvn clean package**
* **mvn exec:java -Dcmapi.ini.file=cmdeploy.ini -Dexec.mainClass="com.cloudera.cmapi.deploy.CMApiDeploy"**

If for some reason you want to start over with clean instances, use the **uninstall.sh** script in the scripts directory.

Details on Deploying Cloudera with The Cloudera Manager API
===========================================================

The following provides a more detailed overview of the process flow to deploy Cloudera with the CM API. There are basically two high-level activities involved in the deployment:

Deploying and preparing instances for the Cloudera deployment. This involves tasks like:
* Provisioning hardware (or VMs) to support the deployment, including the OS, etc.
* Creating required and recommended OS, etc. changes on the hosts, such as disabling SELinux and firewalls, enabling NTPD, making recommended file system changes, etc.
* Installing required Cloudera repositories.
* Installing required software such as a JDK, Cloudera Manager server and agents, etc.
* Starting required services including the Cloudera Manager server and agents.

This stage can be implemented different ways, for example:
* Distribution specific tools like Redhat Kickstart.
* Configuration management tools like Puppet, Chef, or Ansible.
* Shell scripts, as in the example here.

Once cluster hosts are provisioned, the next step is to deploy Cloudera management services and clusters. These are the tasks performed by the automated deployment application. Although there are some requirements in the order of these tasks -- for example the CDH Parcel needs to be available before deploying CDH services -- there's some flexibility in the exact steps involved and order of steps. In general though existing examples will follow this flow fairly closely:
* Initialize cluster(s). This includes setting the cluster name(s) and adding hosts that are part of the cluster(s).
* Deploy and start the CM management services.
* Download, distribute, and activate required Parcels.
* Deploy the cluster(s). This includes deploying services (HDFS, YARN, etc.), perfoming required initialization of the services, and then starting the services.

Comments in the code provide more detailed documentation on the steps involved in each of these tasks.

Details on the Example Implementation
=====================================

This section provides some details on the example deployment code. Before discussing the code though, it's useful to provide a review of some Cloudera Manager concepts and architecture. The following isn't intended to be comprehensive, but is useful in understanding the structure of the example code. More details on CM are available [here](http://www.cloudera.com/documentation/enterprise/latest/topics/cm_intro_primer.html).

The following is a subset of the concepts covered in the above doc that are relevant to this application:

* Cloudera Manager Server: The CM server is the component that manages deployment, management and monitoring of Cloudera clusters. An important thing to note is that a deployment will contain a single CM server.
* Cloudera Manager Agents: Agents run on every node, and are responsible for services such as managing processes, monitoring the host, etc. Each agent communicates with the CM server to update status, receive instructions, etc.
* Management Service: A set of services providing monitoring, reporting, etc. functions. In contrast to the cluster services discussed below, this is a set of services associated with the CM instance.
* Clusters: The set of nodes hosting the Cloudera Hadoop distribution, including all services such as HDSF, Spark, YARN, etc. From the CM standpoint, a cluster is a set of hosts with a specific version of CDH installed, running the Hadoop services ands associated roles. A single CM server can manage multiple clusters, although each cluster can only be associated with a specific CM server.
* Service (or Service Type): A specific piece of functionality managed by CM, generally a standard Hadoop service such as HDFS, YARN, etc.
* Service Instance: This is a specific instance of a service running on a cluster. For example, the label "HDFS-1" may refer to an instance of the HDFS service running on a cluster.
* Role (or Role Type): This is the implementation of specific functionality within a service. For example HDFS has the NameNode, DataNode, etc. roles.
* Role Instance: Similar to service instances, this is a specific instance of a role running on a cluster.
* Role Group: A set of configuration properties applied to a set of role instances.
* Gateway: Sometimes referred to as edge nodes or client nodes, these are nodes that can act as clients to a cluster, but don't run any cluster services.
* Parcel: A binary distribution format for Cloudera deployments, which replace standard packages (e.g. RPMs) for deploying Cloudera.

Another thing to note is how CM defines configurations. A couple of configuration types which are relevant to the code that deploys services are:

* Service Level: Configuration parameters that apply to the entire service. A good example of this is HDFS replication.
* Role Groups: Configurations that apply to roles, such as memory configurations for roles, directory locations, etc.

With the above as background, the following is an overview of the classes in the example application:

* CMApiDeploy: This is a simple driver class. This class loads the configuration, obtains a reference to the CM API root object, and calls methods on the CM Server object (below) to execute deployment.
* CMServer: This class encapsulates the Cloudera Manager Server functionality. Like the CM Server, this class has responsibility for deploying management services and clusters.
* Cluster: Class encapsulating a cluster managed by a CM server. This class is responsible for initializing a cluster, deploying Parcels for the cluster, deploying cluster services, and initializing and starting services.
* ClusterService: Base class for classes encapsulating specific cluster services. This class defines the methods that service classes should implement, and provides default implementations for common methods.
* Service classes: Classes implementing functionality to deploy specific services, including associated roles. These classes, with the exception of ManagementService, extend ClusterService. See below for notes on adding a new service.

Implementing a New Service
==========================

The following are the steps to add code for deploying a new service:

* Update src/main/resources/cmdeploy.ini with parameters for the new service. This includes adding/updating the following, but see the file for detailed comments on the parameters.
  * Add the service to the list of services to be deployed. This is the **services** parameter in the **[CLUSTER]** section.
  * Add a configuration section for the service. This will generally include a section with general configuration parameters such as service name and service hosts, a service configuration section, and then sections for each role configuration. Again see the file for detailed comments.
* Add corresponding variables to com.cloudera.cmapi.deploy.Constants class.
* Add a new class in com.cloudera.cmapi.deploy.services implementing ClusterService. In most cases this should only require copying one of the existing classes and making minimal changes for the new service.
* Update com.cloudera.cmapi.deploy.services.ClusterServiceFactory for the new service class.

Other Notes
===========

The application uses the [ini4j](http://ini4j.sourceforge.net/index.html) library to manage configuration. This isn't necessarily the optimal solution, but it's easy and convenient to use.

TODOS:
======

**General:**
* Add HA support, including HDFS and YARN.
* Add ability to specify the CDH version to deploy.
* Add additional error checking and validation to code.
* Add ability to deploy multiple clusters.
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
