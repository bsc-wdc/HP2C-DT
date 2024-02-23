#!/bin/bash

# Check if at least one argument is provided
if [ $# -eq 0 ]; then
    name_of_deployment="testbed"
    echo "Using default name_of_deployment: $name_of_deployment"
elif [ $# -eq 1 ]; then
    name_of_deployment=$1
    echo "Using name_of_deployment: $name_of_deployment"
else
    echo "Usage: $0 [name_of_deployment]"
    exit 1
fi

# Execute the Python script to create the dashboard JSON
python3 create_json_dashboard.py $name_of_deployment

# Execute the script to create or update the dashboard
./create_or_update_dashboard.sh jsons_dashboards/${name_of_deployment}_dashboard.json

# Execute the script to get the dashboards
./get_dashboards.sh
