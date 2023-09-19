#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

OPAL_DOCKER_IMAGE="opal-client"
CONTAINER_NAME="opal_client"
NETWORK_NAME="hp2c-net"

on_exit(){
    echo "Removing opal container"
    docker rm -f ${CONTAINER_NAME}
}
trap 'on_exit' EXIT

echo "Deploying opal container"

docker run \
    --rm \
    -d \
    --network=${NETWORK_NAME} \
    --name ${CONTAINER_NAME} \
    ${OPAL_DOCKER_IMAGE} \
    mvn exec:java -Dexec.mainClass='bsc.es.hp2c.opalSimulator.OpalSimulator'

echo "Opal client properly deployed"
docker wait ${CONTAINER_NAME}
echo "Ended properly"
