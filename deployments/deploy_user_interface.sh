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

setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup")


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
    -it --rm \
    --name ${DEPLOYMENT_PREFIX}_user_interface \
    -v ${setup_folder}:/data/ \
    -p 80:80 \
    -e DEPLOYMENT_NAME=${DEPLOYMENT_NAME} \
    ${DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"