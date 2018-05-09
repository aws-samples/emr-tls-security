#!/bin/bash
sudo cp /etc/zeppelin/conf/zeppelin-site.xml.template /etc/zeppelin/conf/zeppelin-site.xml
truststorePath=$(grep -Po "(?<=^internal-communication.https.keystore.path = ).*" /etc/presto/conf/config.properties)
truststorePass=$(grep -Po "(?<=^internal-communication.https.keystore.key = ).*" /etc/presto/conf/config.properties)
keystorePath=$(grep -Po "(?<=^http-server.https.keystore.path = ).*" /etc/presto/conf/config.properties)
keystorePass=$(grep -Po "(?<=^http-server.https.keystore.key = ).*" /etc/presto/conf/config.properties)
keymanager=$(grep -Po "(?<=^http-server.https.keymanager.password = ).*" /etc/presto/conf/config.properties)
sudo sed -i '/<name>zeppelin.server.port<\/name>/!b;n;c<value>8890<\/value>' /etc/zeppelin/conf/zeppelin-site.xml
sudo sed -i '/<name>zeppelin.server.ssl.port<\/name>/!b;n;c<value>7773<\/value>' /etc/zeppelin/conf/zeppelin-site.xml
sudo sed -i '/<name>zeppelin.ssl<\/name>/!b;n;c<value>true<\/value>' /etc/zeppelin/conf/zeppelin-site.xml
sudo sed -i '/<name>zeppelin.ssl.keystore.path<\/name>/!b;n;c<value>'"$keystorePath"'<\/value>' /etc/zeppelin/conf/zeppelin-site.xml
sudo sed -i '/<name>zeppelin.ssl.keystore.password<\/name>/!b;n;c<value>'"$keystorePass"'<\/value>' /etc/zeppelin/conf/zeppelin-site.xml
sudo sed -i '/<name>zeppelin.ssl.truststore.path<\/name>/!b;n;c<value>'"$truststorePath"'<\/value>' /etc/zeppelin/conf/zeppelin-site.xml
CONTENT1="<property>\n  <name>zeppelin.ssl.truststore.password</name>\n  <value>${truststorePass}</value>\n</property>"
sudo sed -i '/<\/configuration>/i'"$CONTENT1" /etc/zeppelin/conf/zeppelin-site.xml
CONTENT2="<property>\n  <name>zeppelin.ssl.key.manager.password</name>\n  <value>${keymanager}</value>\n</property>"
sudo sed -i '/<\/configuration>/i'"$CONTENT2" /etc/zeppelin/conf/zeppelin-site.xml
sudo stop zeppelin
sudo start zeppelin

