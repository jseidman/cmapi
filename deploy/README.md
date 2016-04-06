* Deploy instances on Amazon EC2 using a supported OS.
* Update hosts.txt with worker node hostnames. These should be the public hostnames.
* Update cmdeploy.cfg:
** Set cmserver to the public hostname of the instance to host the Cloudera Manager server.
** Set pemfile to point to a valid AWS PEM file.
** Set user to a valid user for the AWS instances. For RHEL/CentOS this is usually ec2-user.
* ./setup-aws-hosts.sh cmdeploy.cfg
* Install MySQL instance to host the Hive metastore and Oozie DBs:
** ./run_install_mysql.sh cmdeploy.cfg

* Update cmdeploy.ini
** Update hostname parameters for services

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
* Consider making CMServer class a singleton.
* Add option to enable Kerberos.
* Explore options to enable encryption.
* Add additional services:
  * Navigator
  * Kudu
  * HBase
  * Accumulo
  * Others?

**Specific classes:**
* com.cloudera.cmapi.deploy.services.KafkaService:
  * Make updates to enable deployment of MirrorMaker role. For now the relevant code is commented out.
* com.cloudera.cmapi.deploy.services.ManagementService
  * Class shares code with com.cloudera.cmapi.deploy.services.ClusterService. Consider refactoring, for example create a new Service base class and move the common code into that class.
  * Currently the deploy method checks for a license, but will proceed if license doesn't exist, which leads to an error. Code should be updated to add option to deploy trial license as well as possibly provide option to upload a license. In the case where no license option exists, the code should throw an exception instead of proceeding.
