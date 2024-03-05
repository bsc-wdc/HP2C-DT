#!/bin/bash

# Check if at least one argument is provided
if [ $# -eq 0 ]; then
    deployment_name="testbed"
    deployment_dir="../../../deployments/testbed/setup"
    echo "Using default deployment_name: $deployment_name"
    echo "Using default deployment_dir: $deployment_dir"
elif [ $# -eq 1 ]; then
    deployment_name=$DEPLOYMENT_NAME
    deployment_dir=$1
else
    deployment_name=$1
    deployment_dir=$2
    echo "Using deployment_name: $1"
    echo "Using deployment_dir: $2"
fi

# Execute the Python script to create the dashboard JSON
python3 create_json_dashboard.py $deployment_name $deployment_dir

# Execute the script to create or update the dashboard
./create_or_update_dashboard.sh jsons_dashboards/${deployment_name}_dashboard.json

# Execute the script to get the dashboards
./get_dashboards.sh
