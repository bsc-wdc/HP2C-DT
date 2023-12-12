#!/bin/bash

# Run one container for each JSON file. Container's name will be "hp2c_" plus
# the label specified in global-properties.label.

# Loading Constants
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

EDGE_DOCKER_IMAGE="hp2c/edge:latest"
MANAGER_DOCKER_IMAGE="compss/agents_manager:3.2"

DEPLOYMENT_PREFIX="hp2c"
NETWORK_NAME="${DEPLOYMENT_PREFIX}-net"

# Create a dictionary containg pairs of label-files (JSON files)
setup_folder=$(realpath "${SCRIPT_DIR}/setup")
declare -A labels_paths
declare -A labels_udp_ports
declare -A labels_tcp_sensors_ports

sorted_setup_folder=($(ls -v "${setup_folder}"/*.json))

for f in "${sorted_setup_folder[@]}"; do
    label=$(jq -r '.["global-properties"].label' "${f}")
    udp_port=$(jq -r '.["global-properties"].comms.udp.ports | keys_unsorted[0]' "${f}")
    tcp_sensors_port=$(jq -r '.["global-properties"]["comms"]["tcp-sensors"].ports | keys_unsorted[0]' "${f}")
    if [ "$label" != "null" ]; then
        labels_paths["${label}"]="${f}"
        labels_udp_ports["${label}"]="${udp_port}"
        labels_tcp_sensors_ports["${label}"]="${tcp_sensors_port}"
    else
        echo "Property 'global-properties.label' not found in ${f}"
    fi
done


# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

custom_ip_address="172.29.128.1"

echo "Local IPv4 Address: $ip_address"
echo "Custom IP Address: $custom_ip_address"


# Setting up trap to clear environment
on_exit(){
    echo "Clearing deployment"

    echo "Removing containers"
    for label in "${!labels_paths[@]}"; do
        docker stop ${DEPLOYMENT_PREFIX}_"$label"
    done

    echo "Removing network"
    if [ ! "$(docker network inspect ${NETWORK_NAME} 2>/dev/null)" == "[]" ]; then
        docker network rm ${NETWORK_NAME} 1>/dev/null 2>/dev/null
    fi
}
trap 'on_exit' EXIT

# Auxiliar application to wait for all container deployed
wait_containers(){
    for label in "${!labels_paths[@]}"; do
        docker wait ${DEPLOYMENT_PREFIX}_"$label"
    done
}


################
# SCRIPT MAIN CODE
#################


# Create network
docker network create hp2c-net > /dev/null 2>/dev/null || { echo "Cannot create network"; exit 1; }


# Start edge containers
edge_idx=0
for label in "${!labels_paths[@]}"; do
    REST_AGENT_PORT=$((4610 + edge_idx))1
    COMM_AGENT_PORT=$((4610 + edge_idx))2
    echo "$label REST port: ${REST_AGENT_PORT}"
    echo "$label COMM port: ${COMM_AGENT_PORT}"

    echo "deploying container for $label"
    docker \
        run \
        -d --rm \
        --name ${DEPLOYMENT_PREFIX}_"$label" \
        -p "${labels_udp_ports[$label]}:${labels_udp_ports[$label]}/udp" \
        -p "${labels_tcp_sensors_ports[$label]}:${labels_tcp_sensors_ports[$label]}/tcp" \
        -v ${labels_paths[$label]}:/data/setup.json \
        -e REST_AGENT_PORT=$REST_AGENT_PORT \
        -e COMM_AGENT_PORT=$COMM_AGENT_PORT \
        -e LOCAL_IP=$ip_address \
        -e CUSTOM_IP=$custom_ip_address \
        ${EDGE_DOCKER_IMAGE}
    edge_idx=$(( edge_idx + 1 ))
done

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"

