#!/bin/bash

usage() {
    echo "Usage: $0 [-h] [--deployment_name=<name>] [--deployment_prefix=<prefix>]" 1>&2
    echo "Options:" 1>&2
    echo "  -h: Show usage instructions" 1>&2
    echo "  --deployment_name=<name>: The name of the deployment (default: testbed)" 1>&2
    echo "  --deployment_prefix=<prefix>: The deployment prefix (default: hp2c)" 1>&2
    exit 1
}

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"
DEPLOYMENT_NAME="testbed"

# Parse command line arguments
pos=1
for arg in "$@"; do
    case $arg in
        -h)
            usage
            ;;
        --deployment_name=*)
            DEPLOYMENT_NAME="${arg#*=}"
            ;;
        --deployment_prefix=*)
            DEPLOYMENT_PREFIX="${arg#*=}"
            ;;
        *)
            if [ $pos -eq 1 ]; then
                DEPLOYMENT_NAME=$1
            else
                echo "Error: Unknown option or argument: $arg"
                usage
            fi
            ;;
    esac
    ((pos++))
done

DOCKER_IMAGE="${DEPLOYMENT_PREFIX}/server:latest"

deployment_json="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json"  # Deployment configuration (IPs, etc.)
setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup") # Edge configuration files
config_json="${SCRIPT_DIR}/../config.json"  # Authentication configuration

echo "deployment_json: ${deployment_json}"
echo "setup_folder: ${setup_folder}"
echo "config_json: ${config_json}"

if [ ! -f "${SCRIPT_DIR}/../config.json" ]; then
  echo "Error: Config file not found in ${SCRIPT_DIR}/../config.json."
  exit 1
fi

if [ ! -f "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json" ];then
  echo "Error: Config file not found in ${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json."
  exit 1
fi

if [ ! -d "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup" ];then
  echo"Error: Setup directory not found in ${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup."
  exit 1
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
    -d -it --rm \
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