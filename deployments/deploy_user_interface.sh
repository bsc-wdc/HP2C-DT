#!/bin/bash

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"

if [ $# -eq 2 ]; then
  DEPLOYMENT_NAME=$1
  DOCKER_IMAGE="$2/user_interface:latest"
elif [ $# -eq 1 ]; then
  DEPLOYMENT_NAME=$1
  DOCKER_IMAGE="hp2c/user_interface:latest"
else
  DEPLOYMENT_NAME="testbed"
  DOCKER_IMAGE="hp2c/user_interface:latest"
fi

deployment_json="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json"  # Deployment configuration (IPs, etc.)
setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup") # Edge configuration files
config_json="${SCRIPT_DIR}/../config.json"  # Authentication configuration

# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

# Auxiliar functions
on_exit(){
    echo "Clearing deployment"
    echo "Removing containers"
    docker stop ${DEPLOYMENT_PREFIX}_user_interface
}
trap 'on_exit' EXIT

wait_containers(){
    docker wait ${DEPLOYMENT_PREFIX}_user_interface
}

####################
# SCRIPT MAIN CODE #
####################

echo "Deploying container for USER_INTERFACE"
docker run \
    -d --rm \
    --name ${DEPLOYMENT_PREFIX}_user_interface \
    -v ${setup_folder}:/data/edge \
    -v ${deployment_json}:/data/deployment_setup.json \
    -v ${config_json}:/run/secrets/config.json \
    -p 80:80 \
    -e DEPLOYMENT_NAME=${DEPLOYMENT_NAME} \
    -e LOCAL_IP=${ip_address} \
    ${DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"