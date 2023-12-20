#!/bin/bash

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"
setup_folder=$(realpath "${SCRIPT_DIR}/setup")

if [ $# -eq 1 ]; then
  DOCKER_IMAGE="$1/server:latest"
else
  DOCKER_IMAGE="hp2c/server:latest"
fi


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

    echo "Removing network"
    if [ ! "$(docker network inspect ${NETWORK_NAME} 2>/dev/null)" == "[]" ]; then
        docker network rm ${NETWORK_NAME} 1>/dev/null 2>/dev/null
    fi
}
trap 'on_exit' EXIT

wait_containers(){
    docker wait ${DEPLOYMENT_PREFIX}_server
}


####################
# SCRIPT MAIN CODE #
####################

echo "Deploying container for SERVER"
docker \
    run \
    -d --rm \
    --name ${DEPLOYMENT_PREFIX}_server \
    -v ${setup_folder}:/data/ \
    -e LOCAL_IP=$ip_address \
    -e CUSTOM_IP=$custom_ip_address \
    ${EDGE_DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"