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
    echo " --metrics, -m: Use the metrics logger (default: false)"
    echo " -e: Execute the edge response test"
    echo " -s: Execute the server response test"
    exit 1
}

# Initialization
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DEPLOYMENT_PREFIX="hp2c"
DEPLOYMENT_NAME="testbed"
COMM_SETUP=""

# Parse command line arguments
pos=1
ENABLE_METRICS=0
EDGE_TEST=0
SERVER_TEST=0

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
        --metrics|-m)
            ENABLE_METRICS=1
            ;;
        -e)
            EDGE_TEST=1
            ;;
        -s)
            SERVER_TEST=1
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
resources_path="/opt/COMPSs/Runtime/configuration/xml/resources/default_resources.xml"
if [ $EDGE_TEST == 1 ]; then
  resources_path="${SCRIPT_DIR}/../experiments/response_time/scripts/edge_resources.xml"
elif [ $SERVER_TEST == 1 ]; then
  resources_path="${SCRIPT_DIR}/../experiments/response_time/scripts/server_resources.xml"
fi
remote_resources_path="/opt/COMPSs/Runtime/configuration/xml/resources/resources.xml"

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

nominal_voltages_deployment="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/nominal_voltages.json"
nominal_voltages_default="${SCRIPT_DIR}/defaults/nominal_voltages.json"

# Check if the deployment-specific file exists
if [ -f "$nominal_voltages_deployment" ]; then
    nominal_voltages_file="$nominal_voltages_deployment"
else
    nominal_voltages_file="$nominal_voltages_default"
fi

echo "Using JSON for deployment communications:   $deployment_json"
echo "Using setup folder:                         $setup_folder"
echo "Using defaults JSON for default edge funcs: $defaults_json"
echo "Using nominal voltages file: $nominal_voltages_file"


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

path_to_setup=""
json_files=("$setup_folder"/*.json)
for json_file in "${json_files[@]}"; do
    if [[ -f "$json_file" ]]; then
        global_properties=$(jq -r '.["global-properties"]' "$json_file" 2>/dev/null)
        if [[ "$global_properties" != "null" ]]; then
            type=$(jq -r '.["global-properties"].type' "$json_file" 2>/dev/null)
            if [[ "$type" == "server" ]]; then
                path_to_setup="$json_file"
                echo "Selected setup file: $path_to_setup"
            fi
        fi
    fi
done

if [[ -z "$path_to_setup" ]]; then
  echo "No valid server JSON file found in directory: $setup_file" >&2
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

custom_ip_address="172.29.128.1"

echo "Local IPv4 Address: $ip_address"
echo "Custom IP Address: $custom_ip_address"
echo


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
    -v ${path_to_setup}:/data/setup.json \
    -v ${deployment_json}:/data/deployment_setup.json \
    -v ${nominal_voltages_file}:/data/nominal_voltages.json \
    -v ${config_json}:/run/secrets/config.json \
    -v ${resources_path}:${remote_resources_path} \
    -p 8080:8080 \
    -e LOCAL_IP=$ip_address \
    -e RESOURCES_PATH=$remote_resources_path \
    -e CUSTOM_IP=$custom_ip_address \
    -e ENABLE_METRICS=$ENABLE_METRICS \
    ${DOCKER_IMAGE}

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"