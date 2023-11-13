#!/bin/bash

# Run one container for each JSON file. Container's name will be "hp2c_" plus
# the label specified in global-properties.label.

# Loading Constants
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DEVICES_DOCKER_IMAGE="hp2c/edges:latest"
MANAGER_DOCKER_IMAGE="compss/agents_manager:3.2"

DEPLOYMENT_PREFIX="hp2c"
NETWORK_NAME="${DEPLOYMENT_PREFIX}-net"

setup_folder=$(realpath "${SCRIPT_DIR}/setup")

# Create a dictionary containg pairs of label-files (JSON files)
declare -A labels
for f in ${setup_folder}/*.json; do
    label=$(jq -r '.["global-properties"].label' "${f}")
    if [ "$label" != "null" ]; then
        labels["${label}"]="${f}" 
    else 
        echo "Property 'global-properties.label' not found in ${f}"
    fi
done



# Setting up trap to clear environment
on_exit(){
    echo "Clearing deployment"

    echo "Removing containers"
    for label in "${!labels[@]}"; do
        docker stop "$label"
    done    

    echo "Removing network"
    if [ ! "$(docker network inspect ${NETWORK_NAME} 2>/dev/null)" == "[]" ]; then
        docker network rm ${NETWORK_NAME} 1>/dev/null 2>/dev/null
    fi
}
trap 'on_exit' EXIT

# Auxiliar application to wait for all container deployed
wait_containers(){
    for label in "${!labels[@]}"; do
        docker wait "$label"
    done   
}


################
# SCRIPT MAIN CODE
#################


# Create network
docker network create hp2c-net > /dev/null 2>/dev/null || { echo "Cannot create network"; exit 1; } 

# Start device containers
device_idx=0
for label in "${!labels[@]}"; do
    REST_AGENT_PORT=$((4610 + device_idx))1
    COMM_AGENT_PORT=$((4610 + device_idx))2
    echo "$label REST port: ${REST_AGENT_PORT}"
    echo "$label COMM port: ${COMM_AGENT_PORT}"

    echo "deploying container for $label"
    docker \
        run \
        -d --rm \
        --name "$label" \
        --network host \
        -v ${labels[$label]}:/data/setup.json \
        -e REST_AGENT_PORT=$REST_AGENT_PORT \
        -e COMM_AGENT_PORT=$COMM_AGENT_PORT \
        ${DEVICES_DOCKER_IMAGE} 
    device_idx=$(( device_idx + 1 ))
done

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"

