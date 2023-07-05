#!/bin/bash

# Loading Constants
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DEVICES_DOCKER_IMAGE="hp2c/devices:latest"
MANAGER_DOCKER_IMAGE="compss/agents_manager:3.2"

DEPLOYMENT_PREFIX="hp2c"
NETWORK_NAME="${DEPLOYMENT_PREFIX}-net"

# Setting up trap to clear environment
on_exit(){
    echo "Clearing deployment"

    echo "Removing containers"
    device_idx=0
    for f in ${setup_folder}/device*.json; do
        docker stop ${DEPLOYMENT_PREFIX}_device_${device_idx}
        device_idx=$(( device_idx + 1 ))
    done    

    echo "Removing network"
    if [ ! "$(docker network inspect ${NETWORK_NAME} 2>/dev/null)" == "[]" ]; then
        docker network rm ${NETWORK_NAME} 1>/dev/null 2>/dev/null
    fi
}
trap 'on_exit' EXIT

# Auxiliar application to wait for all container deployed
wait_containers(){
    device_idx=0
    for f in ${setup_folder}/device*.json; do
        docker wait ${DEPLOYMENT_PREFIX}_device_${device_idx}
        device_idx=$(( device_idx + 1 ))
    done   
}


################
# SCRIPT MAIN CODE
#################

setup_folder=$(realpath "${SCRIPT_DIR}/setup")


# Create network
docker network create hp2c-net > /dev/null 2>/dev/null || { echo "Cannot create network"; exit 1; } 

# Start device containers
device_idx=0
for f in ${setup_folder}/device*.json; do
    echo "deploying container for $f"
    docker \
        run \
        -d --rm \
        --name ${DEPLOYMENT_PREFIX}_device_${device_idx} \
        --net ${NETWORK_NAME} \
        -v ${f}:/data/setup.json \
        ${DEVICES_DOCKER_IMAGE}
    device_idx=$(( device_idx + 1 ))
done

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"

