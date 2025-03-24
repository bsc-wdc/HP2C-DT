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

# Run one container for each JSON file. Container's name will be "hp2c_" plus
# the label specified in global-properties.label.

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

DOCKER_IMAGE="${DEPLOYMENT_PREFIX}/edge:latest"
MANAGER_DOCKER_IMAGE="compss/agents_manager:3.2"
NETWORK_NAME="${DEPLOYMENT_PREFIX}-net"

# Initialize configuration files and directories
defaults_json="${SCRIPT_DIR}/defaults/setup/edge_default.json"  # Edge default configuration
setup_folder=$(realpath "${SCRIPT_DIR}/${DEPLOYMENT_NAME}/setup")

# Deployment communications configuration (IP addresses and ports)
if [ -z "$COMM_SETUP" ]; then
    # If no communication setup is provided, use the one in the corresponding deployment directory
    deployment_json="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/deployment_setup.json"
else
    # If a communication setup is provided, override the deployment configuration and use the one in the defaults directory
    deployment_json="${SCRIPT_DIR}/defaults/deployment_setup_${COMM_SETUP}.json"
fi

units_deployment="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/default_units.json"
units_default="${SCRIPT_DIR}/defaults/default_units.json"

# Check if the deployment-specific file exists
if [ -f "$units_deployment" ]; then
    units_file="$units_deployment"
else
    units_file="$units_default"
fi

echo "Using JSON for deployment communications:   $deployment_json"
echo "Using setup folder:                         $setup_folder"
echo "Using defaults JSON for default edge funcs: $defaults_json"
echo "Using units JSON:                           $units_file"

# Verify the provided files and directories exist
if [ ! -f "${defaults_json}" ]; then
  echo "Error: edge_default not found in ${defaults_json}"
  exit 1
fi

if [ ! -f "${deployment_json}" ];then
  echo "Error: Config file not found in ${deployment_json}."
  exit 1
fi

if [ ! -d "${setup_folder}" ];then
  echo "Error: Setup directory not found in ${setup_folder}."
  exit 1
fi


# Create a dictionary containing pairs of label-files (JSON files)
declare -A labels_paths
declare -A labels_udp_ports
declare -A labels_tcp_sensors_ports

sorted_setup_folder=($(ls -v "${setup_folder}"/*.json))

for f in "${sorted_setup_folder[@]}"; do
    type=$(jq -r '.["global-properties"].type' "${f}")
    if [[ -z "$type" ]]; then
        echo "Error: 'type' is not specified in the input file. Options: edge/server."
        exit 1
    elif [[ "$type" != "edge" && "$type" != "server" ]]; then
        echo "Error: Invalid type '$type'. The 'type' must be specified as either 'edge' or 'server'."
        exit 1
    fi

    label=$(jq -r '.["global-properties"].label' "${f}")
    udp_port=$(jq -r '."global-properties".comms."opal-udp".sensors.port' "${f}")
    tcp_sensors_port=$(jq -r '."global-properties".comms."opal-tcp".sensors.port' "${f}")
    if [ "$label" != "null" ]; then
        if [ "$type" == "edge" ]; then
            labels_paths["${label}"]="${f}"
            labels_udp_ports["${label}"]="${udp_port}"
            labels_tcp_sensors_ports["${label}"]="${tcp_sensors_port}"
        fi
    else
        echo "Property 'global-properties.label' not found in ${f}"
    fi
done

# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi
if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'enxcc[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi
if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'ens3' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

custom_ip_address="172.31.144.1"


# Generate the project XML for each edge if compss-project exists in the JSON
declare -A project_paths
declare -A remote_project_paths
remote_project_path="/opt/COMPSs/Runtime/configuration/xml/projects/project.xml"
resources_template_path="${SCRIPT_DIR}/templates/resources_template.xml"
project_template_path="${SCRIPT_DIR}/templates/project_template.xml"

echo "Using project template:                     $project_template_path"
if [ ! -f "${project_template_path}" ]; then
  echo "Error: Project template not found in ${project_template_path}."
  exit 1
fi

for label in "${!labels_paths[@]}"; do
    remote_project_paths["${label}"]=""
    compss_project=$(jq -r '.compss.project' "${labels_paths[$label]}")

    if [[ "$compss_project" != "null" ]]; then
        project_path="${SCRIPT_DIR}/${DEPLOYMENT_NAME}/project_${label}.xml"
        remote_project_paths["${label}"]="${remote_project_path}"

        cpu=$(jq -r '.compss.project.cpu' "${labels_paths[$label]}")
        arch=$(jq -r '.compss.project.arch' "${labels_paths[$label]}")
        memory=$(jq -r '.compss.project.memory' "${labels_paths[$label]}")
        storage=$(jq -r '.compss.project.storage' "${labels_paths[$label]}")

        # Build optional lines only if the values exist
        [[ "$cpu" != "null" ]] && CPU_LINE="<ComputingUnits>$cpu</ComputingUnits>" || CPU_LINE=""
        [[ "$arch" != "null" ]] && ARCH_LINE="<Architecture>$arch</Architecture>" || ARCH_LINE=""
        [[ "$memory" != "null" ]] && MEMORY_LINE="<Memory><Size>$memory</Size></Memory>" || MEMORY_LINE=""
        [[ "$storage" != "null" ]] && STORAGE_LINE="<Storage><Size>$storage</Size></Storage>" || STORAGE_LINE=""

        # Replace placeholders in the template
        sed -e "s|{{CPU_LINE}}|$CPU_LINE|" \
            -e "s|{{ARCH_LINE}}|$ARCH_LINE|" \
            -e "s|{{MEMORY_LINE}}|$MEMORY_LINE|" \
            -e "s|{{STORAGE_LINE}}|$STORAGE_LINE|" \
            "$project_template_path" > "$project_path"

        echo "Generated project XML for $label at $project_path"
        project_paths["${label}"]="${project_path}"
    else
        project_paths["${label}"]=""
    fi
done


echo "Local IPv4 Address: $ip_address"
echo "Custom IP Address: $custom_ip_address"
echo

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
        docker logs -f ${DEPLOYMENT_PREFIX}_"$label"
        docker wait ${DEPLOYMENT_PREFIX}_"$label"
    done
}

# Create network
docker network create hp2c-net > /dev/null 2>/dev/null || { echo "Cannot create network"; exit 1; }

# Start edge containers
edge_idx=0
for label in "${!labels_paths[@]}"; do
    REST_AGENT_PORT=$((4610 + edge_idx))1
    COMM_AGENT_PORT=$((4610 + edge_idx))2
    echo "$label REST port: ${REST_AGENT_PORT}"
    echo "$label COMM port: ${COMM_AGENT_PORT}"

    project_path="${project_paths[$label]}"
    remote_project_path="${remote_project_paths[$label]}"

    echo "deploying container for $label"
    docker \
        run \
        -d -it --rm \
        --name ${DEPLOYMENT_PREFIX}_"$label" \
        -p "${labels_udp_ports[$label]}:${labels_udp_ports[$label]}/udp" \
        -p "${labels_tcp_sensors_ports[$label]}:${labels_tcp_sensors_ports[$label]}/tcp" \
        -v ${labels_paths[$label]}:/data/setup.json \
        -v ${defaults_json}:/data/edge_default.json \
        -v ${deployment_json}:/data/deployment_setup.json \
        -v ${project_path}:${remote_project_path} \
        -v ${units_file}:/data/default_units.json \
        -v "${resources_template_path}:/data/resources_template.xml" \
        -e REST_AGENT_PORT=$REST_AGENT_PORT \
        -e COMM_AGENT_PORT=$COMM_AGENT_PORT \
        -e PROJECT_PATH=$remote_project_path \
        -e LOCAL_IP=$ip_address \
        -e CUSTOM_IP=$custom_ip_address \
        -e AGENT_NAME=$ip_address \
        -p $REST_AGENT_PORT:$REST_AGENT_PORT \
        -p $COMM_AGENT_PORT:$COMM_AGENT_PORT \
        ${DOCKER_IMAGE}
    edge_idx=$(( edge_idx + 1 ))
done

echo "Testbed properly deployed"
wait_containers
echo "Ended properly"