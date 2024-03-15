#!/bin/bash

usage() {
    echo "Usage: $0 [--deployment_name DEPLOYMENT_NAME] [--deployment_dir DEPLOYMENT_DIR]" 1>&2
    exit 1
}

# Default values
deployment_name="testbed"
deployment_dir="../../../deployments/testbed/setup"

# Parse arguments in command line
while [[ $# -gt 0 ]]; do
    case "$1" in
        --deployment_name)
            deployment_name=$2
            shift 2
            ;;
        --deployment_dir)
            deployment_dir=$2
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

if [ -z ${DEPLOYMENT_NAME} ]; then
    deployment_name="testbed"
else
    deployment_name=$DEPLOYMENT_NAME
fi

echo "Using deployment_name: ${deployment_name}"
echo "Using deployment_dir: ${deployment_dir}"

# Function to extract IP and port from JSON
get_ip_port() {
    local json_file="$1"
    local ip port
    ip=$(jq -r ".grafana.ip" "$json_file")
    port=$(jq -r ".grafana.port" "$json_file")
    echo "$ip:$port"
}

# Check if deployment setup JSON exists in ../../deployments/${deployment_name}/deployment_setup.json
if [[ -f ../../../deployments/${deployment_name}/deployment_setup.json ]]; then
    deployment_setup_file="../../../deployments/${deployment_name}/deployment_setup.json"
elif [[ -f /data/deployment_setup.json ]]; then
    deployment_setup_file="/data/deployment_setup.json"
else
    echo "Deployment setup JSON not found."
    exit 1
fi

# Get Grafana IP and port
grafana_addr=$(get_ip_port "$deployment_setup_file")
# Grafana API URL and key
GRAFANA_URL="http://${grafana_addr}"

if [[ -f ../../../config.json ]]; then
    config_file="../../../config.json"
elif [[ -f /run/secrets/config.json ]]; then
    config_file="/run/secrets/config.json"
else
    echo "Config file not found."
    exit 1
fi

GRAFANA_API_KEY=$(jq -r ".grafana.api_key" "$config_file")

# Define a list of GRAFANA_URLs
URLs=("${GRAFANA_URL}")

# Check if LOCAL_IP is not empty and add it to the list
if [ -n "$LOCAL_IP" ]; then
  URLs+=("http://${LOCAL_IP}:3000")
fi

for url in "${URLs[@]}"; do
  datasource_uid=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${url}/api/datasources | jq -r '.[] | select(.name == "influxdb") | .uid')
done

# Execute the Python script to create the dashboard JSON
python3 create_json_dashboard.py ${deployment_name} ${deployment_dir} ${datasource_uid}

# Execute the script to create or update the dashboard
./create_or_update_dashboard.sh ${deployment_name}

# Execute the script to get the dashboards
./get_dashboards.sh ${deployment_name}
