export ROOT_DIR="."
export GRAFANA_API_KEY="glsa_MMyee1K5yzSOFEOQ0Rcx5llirdHt5Jle_6b10b6a4"
export GRAFHOST="http://localhost:3000"

mkdir -p ${ROOT_DIR}/dashboards
for uid in $(curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${GRAFHOST}/api/search | jq '.[].uid' -r); do 
    curl -sk -H "Authorization: Bearer ${GRAFANA_API_KEY}" ${GRAFHOST}/api/dashboards/uid/$uid | jq . > ${ROOT_DIR}/dashboards/$uid.json 
done
