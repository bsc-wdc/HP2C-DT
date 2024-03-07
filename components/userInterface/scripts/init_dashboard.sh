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


# Execute the Python script to create the dashboard JSON
python3 create_json_dashboard.py ${deployment_name} ${deployment_dir}

# Execute the script to create or update the dashboard
./create_or_update_dashboard.sh ${deployment_name}

# Execute the script to get the dashboards
./get_dashboards.sh ${deployment_name}
