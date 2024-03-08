#!/bin/bash

#(local)
if [ -z "${DEPLOYMENT_NAME}" ]; then
    deployment_name="$1"
#(get enviroment variables declared in deploy_user_interface)
else
    deployment_name=$DEPLOYMENT_NAME
fi

# Function to extract IP and port from JSON
get_ip_port() {
    local json_file="$1"
    local ip port
    ip=$(jq -r ".grafana.ip" "$json_file")
    port=$(jq -r ".grafana.port" "$json_file")
    echo "$ip:$port"
}

# Check if deployment setup JSON exists in ../../deployments/${deployment_name}/deployment_setup.json
# (local)
if [[ -f ../../../deployments/${deployment_name}/deployment_setup.json ]]; then
    deployment_setup_file="../../../deployments/${deployment_name}/deployment_setup.json"
# (docker)
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

# Path to the dashboard JSON file
DASHBOARD_JSON="jsons_dashboards/$1_dashboard.json"

# Define a list of GRAFANA_URLs
URLs=("${GRAFANA_URL}")

# Check if LOCAL_IP is not empty and add it to the list
if [ -n "$LOCAL_IP" ]; then
  URLs+=("http://${LOCAL_IP}:3000")
fi

# Iterate over the list of URLs and try to make the request
for url in "${URLs[@]}"; do
  response=$(curl -X POST \
    -H "Authorization: Bearer ${GRAFANA_API_KEY}" \
    -H "Content-Type: application/json" \
    -d @"${DASHBOARD_JSON}" \
    -s -w "%{http_code}" \
    "${url}/api/dashboards/db")
  # Extract the HTTP response status
  http_status=$(echo "$response" | tail -c 4)
  # If the status is 200 (OK), the dashboard was created or updated successfully
  if [ "$http_status" -eq 200 ]; then
    echo "Dashboard created or updated successfully using $url."
    break
  else
    echo "Failed to create or update the dashboard using $url. HTTP Status: $http_status"
  fi
done
