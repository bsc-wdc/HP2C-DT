#!/bin/bash

# Grafana API URL and key
GRAFANA_URL="http://localhost:3000"
GRAFANA_API_KEY="glsa_MMyee1K5yzSOFEOQ0Rcx5llirdHt5Jle_6b10b6a4"

# Path to the dashboard JSON file
DASHBOARD_JSON="$1"


get_dashboard_uid() {
    local title="$1"
    local uid_out
    
    local uid
    for uid in $(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" "${GRAFANA_URL}/api/search" | jq -r '.[].uid'); do 
        local dashboard_json
        dashboard_json=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" "${GRAFANA_URL}/api/dashboards/uid/${uid}")
        local dashboard_title
        dashboard_title=$(echo "$dashboard_json" | jq -r '.dashboard.title')
        if [[ "$dashboard_title" == "$title" ]]; then
            uid_out="$uid"
            break
        fi
    done
    echo "$uid_out"
}


# Make a POST request to create or update the dashboard
response=$(curl -X POST \
  -H "Authorization: Bearer ${GRAFANA_API_KEY}" \
  -H "Content-Type: application/json" \
  -d @"${DASHBOARD_JSON}" \
  -s -w "%{http_code}" \
  "${GRAFANA_URL}/api/dashboards/db")

# Extract the HTTP response status
http_status=$(echo "$response" | tail -c 4)
echo "$http_status"
# If the status is 200 (OK), the dashboard was created or updated successfully
if [ "$http_status" -eq 200 ]; then
  echo "Dashboard created or update successfully."
  # Extract the name of the existing dashboard from the JSON request
  dashboard_name=$(jq -r '.dashboard.title' "${DASHBOARD_JSON}")
  # Extract the UID of the existing dashboard from the search results
  uid=$(get_dashboard_uid "$dashboard_name")
  echo "Dashboard UID: $uid"
else
  echo "Failed to create or update the dashboard. HTTP Status: $http_status"
fi
