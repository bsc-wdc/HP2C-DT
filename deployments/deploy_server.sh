#!/bin/bash

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"
setup_folder=$(realpath "${SCRIPT_DIR}/setup")  # Edge configuration files
config_json="${SCRIPT_DIR}/../../config.json"  # Authentication configuration
deployment_json="${SCRIPT_DIR}/deployment_setup.json"  # Deployment configuration (IPs, etc.)

if [ $# -eq 2 ]; then
  DEPLOYMENT_NAME=$1
  DOCKER_IMAGE="$2/server:latest"
elif [ $# -eq 1 ]; then
  DEPLOYMENT_NAME=$1
  DOCKER_IMAGE="hp2c/server:latest"
else
  DEPLOYMENT_NAME="testbed"
  DOCKER_IMAGE="hp2c/server:latest"
fi

setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup")


# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

custom_ip_address="172.29.128.1"

echo "Local IPv4 Address: $ip_address"
echo "Custom IP Address: $custom_ip_address"


# Auxiliar functions
on_exit(){
    echo "Clearing deployment"

    echo "Removing containers"
    docker stop ${DEPLOYMENT_PREFIX}_server
}
trap 'on_exit' EXIT

wait_containers(){
    docker wait ${DEPLOYMENT_PREFIX}_server
}


####################
# SCRIPT MAIN CODE #
####################

echo "Deploying container for SERVER with REST API listening on port 8080..."
docker run \
    -it -d --rm \
    --name ${DEPLOYMENT_PREFIX}_server \
    -v ${setup_folder}:/data/edge/ \
    -v ${deployment_json}:/data/deployment_setup.json \
    -v ${config_json}:/run/secrets/config.json \
    -p 8080:8080 \
    -e LOCAL_IP=$ip_address \
    -e CUSTOM_IP=$custom_ip_address \
    ${DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"