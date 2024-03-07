export ROOT_DIR="."

if [ -z ${DEPLOYMENT_NAME} ]; then
    deployment_name="$1"
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

mkdir -p ${ROOT_DIR}/dashboards

for uid in $(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${GRAFANA_URL}/api/search | jq '.[].uid' -r); do
    dashboard_info=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${GRAFANA_URL}/api/dashboards/uid/$uid)
    title=$(echo $dashboard_info | jq -r '.dashboard.title')
    if echo "$title" | grep -q "hp2cdt - "; then
        name_of_deployment=$(echo "$title" | awk -F "hp2cdt - " '{print $2}' | tr -d '[:space:]')
    else
        name_of_deployment="$title"
    fi

    echo "Exporting dashboard with UID: $uid to file: $name_of_deployment.json"
    echo $dashboard_info | jq . > ${ROOT_DIR}/dashboards/$name_of_deployment.json
done
