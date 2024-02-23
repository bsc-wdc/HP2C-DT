export ROOT_DIR="."
export GRAFANA_API_KEY="glsa_MMyee1K5yzSOFEOQ0Rcx5llirdHt5Jle_6b10b6a4"
export GRAFHOST="http://localhost:3000"

mkdir -p ${ROOT_DIR}/dashboards

for uid in $(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${GRAFHOST}/api/search | jq '.[].uid' -r); do 
    dashboard_info=$(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${GRAFHOST}/api/dashboards/uid/$uid)
    title=$(echo $dashboard_info | jq -r '.dashboard.title')
    if echo "$title" | grep -q "hp2cdt - "; then
        name_of_deployment=$(echo "$title" | awk -F "hp2cdt - " '{print $2}' | tr -d '[:space:]')
    else
        name_of_deployment="$title"
    fi

    echo "Exporting dashboard with UID: $uid to file: $name_of_deployment.json"
    echo $dashboard_info | jq . > ${ROOT_DIR}/dashboards/$name_of_deployment.json
done
