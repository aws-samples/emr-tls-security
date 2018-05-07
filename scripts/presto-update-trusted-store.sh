#!/bin/bash
truststorePass=$(grep -Po "(?<=^internal-communication.https.keystore.key = ).*" /etc/presto/conf/config.properties)
sudo keytool -importkeystore -srckeystore /usr/share/aws/emr/security/conf/truststore.jks -destkeystore /usr/lib/jvm/java/jre/lib/security/cacerts -deststorepass changeit -srcstorepass $truststorePass

