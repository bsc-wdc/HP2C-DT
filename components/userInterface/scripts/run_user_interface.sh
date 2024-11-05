#!/bin/bash
export INITIAL_EXECUTION="1"

usage() {
    echo "Usage: $0 [-h] [--deployment_name=<name>] [--deployment_prefix=<prefix>]" 1>&2
    echo "Options:" 1>&2
    echo "  -h: Show usage instructions" 1>&2
    echo "  --deployment_name=<name>: The name of the deployment (default: testbed)" 1>&2
    echo "  --comm=<mode>: The communication mode. Overrides 'deployment_setup.json'
                 with one of the file in 'defaults/'. Options are:
                    local
                    bsc
                    bsc_subnet
                    (default: None)" 1>&2
    exit 1
}

docker=0
if [ -f /.dockerenv ]; then
    docker=1
    deployment_json="/data/deployment_setup.json"
else
    # Initialization
    SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
    COMM_SETUP=""
    DEPLOYMENT_NAME="testbed"

    # Parse command line arguments
    pos=1
    for arg in "$@"; do
        case $arg in
            -h)
                usage
                ;;
            --deployment_name=*)
                DEPLOYMENT_NAME="${arg#*=}"
                ;;
            --comm=*)
                COMM_SETUP="${arg#*=}"
                ;;
            *)
                if [ $pos -eq 1 ]; then
                    DEPLOYMENT_NAME=$1
                else
                    echo "Error: Unknown option or argument: $arg"
                    usage
                fi
                ;;
        esac
        ((pos++))
    done

    # Deployment communications configuration (IP addresses and ports)
    if [ -z "$COMM_SETUP" ]; then
        # If no communication setup is provided, use the one in the corresponding deployment directory
        deployment_json="${SCRIPT_DIR}/../../../deployments/${DEPLOYMENT_NAME}/deployment_setup.json"
    else
        # If a communication setup is provided, override the deployment configuration and use the one in the defaults directory
        deployment_json="${SCRIPT_DIR}/../../../deployments/defaults/deployment_setup_${COMM_SETUP}.json"
    fi

    if [ ! -f "${deployment_json}" ];then
      echo "Error: Config file not found in ${deployment_json}."
      exit 1
    fi
fi

export DEPLOYMENT_JSON="${deployment_json}"

# If the user interface is deployed in docker (using deploy_user_interface.sh), DEPLOYMENT_NAME is exported in the
# docker run. Otherwise, it is declared above
if [ -n "$DEPLOYMENT_NAME" ]; then
    export DEPLOYMENT_NAME=$DEPLOYMENT_NAME
fi

USER_INTERFACE_PORT=$(jq -r '.user_interface.port' "$deployment_json")

if [ $docker -eq 0 ]; then
    cd ..
    rm -f **/db.sqlite3
    #python3 manage.py flush --no-input
    python3 manage.py makemigrations
    python3 manage.py migrate --run-syncdb
    python3 manage.py collectstatic --noinput
    python3 manage.py runserver 0.0.0.0:"${USER_INTERFACE_PORT}" |  tee $HOME/log.log
else
    python3 ../manage.py makemigrations
    python3 ../manage.py migrate --run-syncdb
    python3 ../manage.py collectstatic --noinput
    python3 ../manage.py runserver 0.0.0.0:"${USER_INTERFACE_PORT}"
fi
