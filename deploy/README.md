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

TODOS:

* Add support for HA
* Add Navigator

Adding a service:

* Add configuration parameters to resources/cmdeploy.ini. See that file for
 details.
** Add service to services parameter in [CLUSTER] section
** Add configuration sections for service
* Add corresponding variables to com.cloudera.cmapi.deploy.Constants.
* Add a new class in com.cloudera.cmapi.deploy.services implementing ClusterService.
* Update com.cloudera.cmapi.deploy.services.ClusterServiceFactory
* Update cmdeploy.ini by adding new service to "services" parameter in the "[CLUSTER]" section. 
