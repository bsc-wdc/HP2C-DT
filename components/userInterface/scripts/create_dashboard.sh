GRAFANA_URL="http://localhost:3000"
GRAFANA_API_KEY="glsa_MMyee1K5yzSOFEOQ0Rcx5llirdHt5Jle_6b10b6a4"

DASHBOARD_JSON="$1"

curl -X POST \
  -H "Authorization: Bearer ${GRAFANA_API_KEY}" \
  -H "Content-Type: application/json" \
  -d @"${DASHBOARD_JSON}" \
  "${GRAFANA_URL}/api/dashboards/db"
