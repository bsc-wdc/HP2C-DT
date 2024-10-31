#!/bin/bash

usage() {
    echo "Usage: $0 [-h] [--deployment_name=<name>] [--deployment_prefix=<prefix>]" 1>&2
    echo "Options:" 1>&2
    echo "  -h: Show usage instructions" 1>&2
    echo "  --deployment_name=<name>: The name of the deployment (default: testbed)" 1>&2
    echo "  --deployment_prefix=<prefix>: The deployment prefix (default: hp2c)" 1>&2
    echo "  --comm=<mode>: The communication mode. Overrides 'deployment_setup.json' 
                 with one of the file in 'defaults/'. Options are: 
                    local
                    bsc
                    bsc_subnet
                    (default: None)" 1>&2
    exit 1
}

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"
DEPLOYMENT_NAME="testbed"
COMM_SETUP=""

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
        --comm=*)
            COMM_SETUP="${arg#*=}"
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

DOCKER_IMAGE="${DEPLOYMENT_PREFIX}/user_interface:latest"


# Initialize configuration files and directories
setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup") # Edge configuration files
config_json="${SCRIPT_DIR}/../config.json"  # Authentication configuration

# Deployment communications configuration (IP addresses and ports)
if [ -z "$COMM_SETUP" ]; then
    # If no communication setup is provided, use the one in the corresponding deployment directory
    deployment_json="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json"  
else
    # If a communication setup is provided, override the deployment configuration and use the one in the defaults directory
    deployment_json="${SCRIPT_DIR}/defaults/deployment_setup_${COMM_SETUP}.json"
fi

echo "Using JSON for deployment communications:   $deployment_json"
echo "Using setup folder:                         $setup_folder"
echo "Using defaults JSON for default edge funcs: $defaults_json"


# Verify the provided files and directories exist
if [ ! -f "${SCRIPT_DIR}/../config.json" ]; then
  echo "Error: Config file not found in ${SCRIPT_DIR}/../config.json."
  exit 1
fi

if [ ! -f "${deployment_json}" ];then
  echo "Error: Config file not found in ${deployment_json}."
  exit 1
fi

if [ ! -d "${setup_folder}" ];then
  echo"Error: Setup directory not found in ${setup_folder}."
  exit 1
fi

# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi
if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'enxcc[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
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
    -d -it --rm \
    --name ${DEPLOYMENT_PREFIX}_user_interface \
    -v ${setup_folder}:/data/edge \
    -v ${deployment_json}:/data/deployment_setup.json \
    -v ${config_json}:/run/secrets/config.json \
    -p 8000:8000 \
    -e DEPLOYMENT_NAME=${DEPLOYMENT_NAME} \
    -e LOCAL_IP=${ip_address} \
    ${DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"