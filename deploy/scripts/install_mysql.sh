# Install MySQL and create Hive metastore objects and Oozie DB. Note that these
# steps create a non-secure MySQL install and are for test purposes only.
# This script also assumes the Hive Metastore Server and Ooozie Server  will run
# on the same host as MySQL. If this isn't the case modify the create user
# commands accordingly. 

sudo yum -y install mysql-server
sudo service mysqld start
sudo /sbin/chkconfig mysqld on
mysql -uroot --execute="CREATE DATABASE metastore; USE metastore; SOURCE ./hive-schema-1.1.0.mysql.sql;"
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

