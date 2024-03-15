#!/bin/bash

DEPLOYMENT_NAME="testbed"
ORG_NAME="hp2c"
HP2C_VERSION="1.0"

if [ $# -ge 1 ]; then
  DEPLOYMENT_NAME=$1
fi
if [ $# -ge 2 ]; then
  ORG_NAME=$2
fi
if [ $# -ge 3 ]; then
  HP2C_VERSION=$3
fi

# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

custom_ip_address="172.29.128.1"

#SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
#DIRECTORY="$SCRIPT_DIR/$DEPLOYMENT_NAME/setup"
#BASE_TCP_ACTUATORS_PORT=99999

#if [ -d "$DIRECTORY" ]; then
#    NUMBER_OF_EDGES=$(ls -1 "$DIRECTORY" | wc -l)
#    for file in "$DIRECTORY"/*.json; do
#          if [ -f "$file" ]; then
#            echo "$file"
#              # get actuators port from edge setup
#              port=$(jq '."global-properties".comms."opal-tcp".actuators.port' "$file")
#              port="${port%\"}"
#              port="${port#\"}"
#              port=$(expr "$port" + 0)
#              if [ "$port" -lt "$BASE_TCP_ACTUATORS_PORT" ]; then
#                  BASE_TCP_ACTUATORS_PORT=$port
#              fi
#          fi
#    done
#else
#    echo "Deployment setup directory $DIRECTORY does not exist."
# fi

echo "DEPLOYMENT NAME"
export DEPLOYMENT_NAME=$DEPLOYMENT_NAME
export ORG_NAME=$ORG_NAME
export HP2C_VERSION=$HP2C_VERSION
export CUSTOM_IP=$custom_ip_address
export LOCAL_IP=$ip_address
#export NUMBER_OF_EDGES=$NUMBER_OF_EDGES
#export BASE_TCP_ACTUATORS_PORT=$BASE_TCP_ACTUATORS_PORT
#start_port=$((BASE_TCP_ACTUATORS_PORT / 1000))
#end_port=$(( ((BASE_TCP_ACTUATORS_PORT + NUMBER_OF_EDGES - 1) / 1000) + 1))

#echo "${start_port}-${end_port}:${BASE_TCP_ACTUATORS_PORT}-${BASE_TCP_ACTUATORS_PORT + NUMBER_OF_EDGES - 1}"

cd ../docker

docker-compose up --build
