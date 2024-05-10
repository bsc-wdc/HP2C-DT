#!/bin/bash

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"
SIMULATION_NAME=""
DEPLOYMENT_NAME="testbed"
TIME_STEP=1000

# Parse command line arguments
pos=1
for arg in "$@"; do
    case $arg in
        -deployment_name=*)
            DEPLOYMENT_NAME="${arg#*=}"
            ;;
        -simulation_name=*)
            SIMULATION_NAME="${arg#*=}"
            ;;
        -time_step=*)
            TIME_STEP="${arg#*=}"
            ;;
        *)
            if [ $pos -eq 1 ]; then
              DEPLOYMENT_NAME=$1
            else
              echo "Error: Unknown option or argument: $arg"
              exit 1
            fi
            ;;
    esac
    ((pos++))
done
DOCKER_IMAGE="${DEPLOYMENT_PREFIX}/opal_simulator:1.0"


setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup") # Edge configuration files

if [ ! -d $setup_folder ];then
  echo"Error: Config file not found in hp2cdt/deployments/${DEPLOYMENT_NAME}/setup."
  exit 1
fi


# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

# Auxiliar functions
on_exit(){
    echo "Clearing deployment"
    echo "Removing containers"
    docker stop ${DEPLOYMENT_PREFIX}_opal_simulator
}
trap 'on_exit' EXIT

wait_containers(){
    docker wait ${DEPLOYMENT_PREFIX}_opal_simulator
}

####################
# SCRIPT MAIN CODE #
####################

echo "Deploying container for USER_INTERFACE"
docker run \
    -d --rm \
    --name ${DEPLOYMENT_PREFIX}_opal_simulator \
    -v ${setup_folder}:/data/edge \
    -p 80:80 \
    -e DEPLOYMENT_NAME=${DEPLOYMENT_NAME} \
    -e LOCAL_IP=${ip_address} \
    -e TIME_STEP=$TIME_STEP \
    $( [ -n "$SIMULATION_NAME" ] && echo "-e SIMULATION_NAME=${SIMULATION_NAME}" ) \
    ${DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"