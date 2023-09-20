#!/bin/bash

# Loading Constants
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DEVICES_DOCKER_IMAGE="hp2c/devices:latest"
MANAGER_DOCKER_IMAGE="compss/agents_manager:3.2"

DEPLOYMENT_PREFIX="hp2c"
NETWORK_NAME="${DEPLOYMENT_PREFIX}-net"
BASE_PORT=8080
UDP_PORT=8080


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
    mapped_port=$(( BASE_PORT + device_idx ))
    REST_AGENT_PORT=$((4610 + device_idx))1
    COMM_AGENT_PORT=$((4610 + device_idx))2
    echo "device${device_idx} UDP port: ${mapped_port}"
    echo "device${device_idx} REST port: ${REST_AGENT_PORT}"
    echo "device${device_idx} COMM port: ${COMM_AGENT_PORT}"

    echo "deploying container for $f"
    docker \
        run \
        -d --rm \
        --name ${DEPLOYMENT_PREFIX}_device_${device_idx} \
        --network host \
        -p ${UDP_PORT}:${mapped_port} \
        -v ${f}:/data/setup.json \
        -e REST_AGENT_PORT=$REST_AGENT_PORT \
        -e COMM_AGENT_PORT=$COMM_AGENT_PORT \
        ${DEVICES_DOCKER_IMAGE} 
    device_idx=$(( device_idx + 1 ))
done

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"

