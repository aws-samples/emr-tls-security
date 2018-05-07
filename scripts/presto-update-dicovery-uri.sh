#!/bin/bash
URL=$1
function timestamp {
  date +"%Y-%m-%d %T:: "
}

#######################################

function changePrestoConfig {
    while true;
    do
        if (( $(initctl list | grep presto-server | grep running | wc -l) > 0 ))
        then
            echo $(timestamp) "Presto Service is running!!!"

            if [ -e /etc/presto/conf/config.properties ];
            then
                echo $(timestamp) "Changing Discovery uri"
                sudo sed -i "s/discovery.uri.*/discovery.uri = https:\/\/${URL}:8446/" /etc/presto/conf/config.properties

                source /etc/presto/conf/config.properties
                if [ "$coordinator" == "true" ];
                then
                   echo $(timestamp) "Changing Master Node Address"
                   sudo sed -i "s/node.internal-address.*/node.internal-address = ${URL}/" /etc/presto/conf/config.properties
                fi
                echo $(timestamp) "Restart Presto"
                sudo stop presto-server
                if [ $? -ne 0 ];
                then
                    echo $(timestamp) "Could not stop Presto Service. Waiting"
                    sleep 30
                else
                    sudo start presto-server
                    return 0
                fi
            else
                echo $(timestamp) "Config Files not found yet, Waiting"
                sleep 30
            fi

        else
            echo $(timestamp) "Presto Server not running. Waiting for service to come up"
            sleep 30
        fi
    done
}

changePrestoConfig
