#!/bin/bash

usage() {
    echo "Usage: $0 [--deployment_name DEPLOYMENT_NAME] [--deployment_dir DEPLOYMENT_DIR]" 1>&2
    exit 1
}

#################################### INIT #################################

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

#get database_ip
database_ip=$(jq -r ".database.ip" "$deployment_setup_file")

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

DATABASE_USERNAME=$(jq -r ".database.username" "$config_file")
DATABASE_PASSWORD=$(jq -r ".database.password" "$config_file")

# Define a list of GRAFANA_URLs
URLs=("${GRAFANA_URL}")

# Check if LOCAL_IP is not empty and add it to the list
if [ -n "$LOCAL_IP" ]; then
  URLs+=("http://${LOCAL_IP}:3000")
fi



############################ GET DATASOURCE UID ######################################
datasource_uid=""
for url in "${URLs[@]}"; do
  uid=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${url}/api/datasources | jq -r '.[] | select(.name == "influxdb") | .uid')
  if [ -n "$uid" ]; then
    URL=$url
    datasource_uid=$uid
    response=$(curl -X DELETE \
                -H "Authorization: Bearer ${GRAFANA_API_KEY}" \
                -H "Content-Type: application/json" \
                "${URL}/api/datasources/uid/${datasource_uid}")
    echo "Delete response $response"
  fi
done

############################ CREATE DATASOURCE #######################################
INFLUXDB_JSON="{
  \"name\": \"influxdb\",
  \"type\": \"influxdb\",
  \"typeName\": \"InfluxDB\",
  \"typeLogoUrl\": \"/public/app/plugins/datasource/influxdb/img/influxdb_logo.svg\",
  \"access\": \"proxy\",
  \"url\": \"http://${database_ip}:8086\",
  \"user\": \"${DATABASE_USERNAME}\",
  \"database\": \"\",
  \"basicAuth\": false,
  \"isDefault\": true,
  \"jsonData\": {
    \"dbName\": \"hp2cdt\"
  },
  \"secureJsonData\": {
        \"password\": \"${DATABASE_PASSWORD}\"
  },
  \"readOnly\": false
}"

if [ -n "$URL" ]; then
  URLs=("$URL")
fi

datasource_uid=""
for url in "${URLs[@]}"; do
  response=$(curl -X POST \
      -H "Authorization: Bearer ${GRAFANA_API_KEY}" \
      -H "Content-Type: application/json" \
      -d "$(echo "$INFLUXDB_JSON" | jq -c)" \
      "${url}/api/datasources")

  uid=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${url}/api/datasources | jq -r '.[] | select(.name == "influxdb") | .uid')
  if [ -n "$uid" ]; then
    datasource_uid=$uid
  fi
done

if [ -z "$datasource_uid" ]; then
  echo "Datasource influxdb uid not found"
  exit 1
fi

########################### CREATE JSON DASHBOARD ####################################
# Execute the Python script to create the dashboard JSON
python3 create_json_dashboard.py ${deployment_name} ${deployment_dir} ${datasource_uid}



########################### CREATE OR UPDATE DASHBOARD ###############################
# Path to the dashboard JSON file
DASHBOARD_JSON="jsons_dashboards/${deployment_name}_dashboard.json"

# Iterate over the list of URLs and try to make the request
final_url=""
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
    final_url=$url
    break
  else
    echo $http_status
  fi
done

if [ -n "$final_url" ]; then
    echo "Dashboard created or updated successfully using ${final_url}."
else
  echo "Failed to create or update the dashboard"
  exit 1
fi


############################## GET DASHBOARDS #########################################
mkdir -p dashboards

for url in "${URLs[@]}"; do
  for uid in $(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${url}/api/search | jq '.[].uid' -r); do
      dashboard_info=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${url}/api/dashboards/uid/$uid)
      title=$(echo $dashboard_info | jq -r '.dashboard.title')
      if echo "$title" | grep -q "hp2cdt - "; then
          name_of_deployment=$(echo "$title" | awk -F "hp2cdt - " '{print $2}' | tr -d '[:space:]')
      else
          name_of_deployment="$title"
      fi

      echo "Exporting dashboard with UID: $uid to file: $name_of_deployment.json"
      echo $dashboard_info | jq . > "dashboards/$name_of_deployment.json"
  done
done
