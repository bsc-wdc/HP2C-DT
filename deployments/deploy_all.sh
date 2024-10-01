#!/bin/bash
usage() {
    echo "Usage: $0 [-h] [--deployment_name=<name>] [--simulation_name=<name>] [--time_step=<value>] [--deployment_prefix=<prefix>] [--hp2c_version=<version>]" 1>&2
    echo "Options:" 1>&2
    echo "  -h: Show usage instructions" 1>&2
    echo "  --deployment_name=<name>: The name of the deployment (default: testbed)" 1>&2
    echo "  --simulation_name=<name>: The name of the simulation" 1>&2
    echo "  --time_step=<value>: The time step value (default: 1000)" 1>&2
    echo "  --deployment_prefix=<prefix>: The deployment prefix (default: hp2c)" 1>&2
    echo "  --hp2c_version=<version>: The version of HP2C (default: 1.0)" 1>&2
    echo "  --comm=<mode>: The communication mode. If provided, overrides 'deployment_setup.json' 
                 and uses the corresponding one at the 'defaults/' directory. 
                 e.g., if '--comm=bsc', it uses the configuration file
                 'defaults/deployment_setup_bsc.json' (default: 'local')" 1>&2
    exit 1
}

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DEPLOYMENT_NAME="testbed"
DEPLOYMENT_PREFIX="hp2c"
HP2C_VERSION="1.0"
SIMULATION_NAME=""
TIME_STEP=1000
COMM_SETUP="local"

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
        --simulation_name=*)
            SIMULATION_NAME="${arg#*=}"
            ;;
        --time_step=*)
            TIME_STEP="${arg#*=}"
            ;;
        --deployment_prefix=*)
            DEPLOYMENT_PREFIX="${arg#*=}"
            ;;
        --hp2c_version=*)
            HP2C_VERSION="${arg#*=}"
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


# Deployment communications configuration (IP addresses and ports)
if [ -z "$COMM_SETUP" ]; then
    # If no communication setup is provided, use the one in the corresponding deployment directory
    deployment_json="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json"  
else
    # If a communication setup is provided, override the deployment configuration and use the one in the defaults directory
    deployment_json="${SCRIPT_DIR}/defaults/deployment_setup_${COMM_SETUP}.json"
fi

# Echo all possible variables
echo "DEPLOYMENT_NAME=$DEPLOYMENT_NAME"
echo "SIMULATION_NAME=$SIMULATION_NAME"
echo "TIME_STEP=$TIME_STEP"
echo "DEPLOYMENT_PREFIX=$DEPLOYMENT_PREFIX"
echo "HP2C_VERSION=$HP2C_VERSION"
echo "DEPLOYMENT_SETUP_JSON=$deployment_json"

# Verify the provided files and directories exist
if [ ! -f "${SCRIPT_DIR}/../config.json" ]; then
  echo "Error: Config file not found in ${SCRIPT_DIR}/../config.json."
  exit 1
fi

if [ "$SIMULATION_NAME" != "" ] && [ ! -f "${SCRIPT_DIR}/../components/opalSimulator/simulations/${SIMULATION_NAME}.csv" ]; then
  echo "Error: Simulation file not found in ${SCRIPT_DIR}/../components/opalSimulator/simulations/${SIMULATION_NAME}.csv."
  exit 1
fi

if [ ! -f "$deployment_json" ];then
  echo "Error: Config file not found in ${deployment_json}."
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

#SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
#DIRECTORY="$SCRIPT_DIR/$DEPLOYMENT_NAME/setup"
#BASE_TCP_ACTUATORS_PORT=99999

#if [ -d "$DIRECTORY" ]; then
#    NUMBER_OF_EDGES=$(ls -1 "$DIRECTORY" | wc -l)
#    for file in "$DIRECTORY"/*.json; do
#          if [ -f "$file" ]; then
#            echo "$file"
#              # get actuators port from edge setup
#              port=$(jq '."global-properties".comms."opal-tcp".actuators.port' "$file")
#              port="${port%\"}"
#              port="${port#\"}"
#              port=$(expr "$port" + 0)
#              if [ "$port" -lt "$BASE_TCP_ACTUATORS_PORT" ]; then
#                  BASE_TCP_ACTUATORS_PORT=$port
#              fi
#          fi
#    done
#else
#    echo "Deployment setup directory $DIRECTORY does not exist."
# fi

export DEPLOYMENT_NAME=$DEPLOYMENT_NAME
export DEPLOYMENT_PREFIX=$DEPLOYMENT_PREFIX
export HP2C_VERSION=$HP2C_VERSION
export CUSTOM_IP=$custom_ip_address
export LOCAL_IP=$ip_address
export TIME_STEP=$TIME_STEP
export SIMULATION_NAME=$SIMULATION_NAME
export DEPLOYMENT_SETUP_JSON=$deployment_json
#export NUMBER_OF_EDGES=$NUMBER_OF_EDGES
#export BASE_TCP_ACTUATORS_PORT=$BASE_TCP_ACTUATORS_PORT
#start_port=$((BASE_TCP_ACTUATORS_PORT / 1000))
#end_port=$(( ((BASE_TCP_ACTUATORS_PORT + NUMBER_OF_EDGES - 1) / 1000) + 1))

#echo "${start_port}-${end_port}:${BASE_TCP_ACTUATORS_PORT}-${BASE_TCP_ACTUATORS_PORT + NUMBER_OF_EDGES - 1}"

cd ../docker

docker compose up --build
