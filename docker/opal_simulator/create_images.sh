#!/bin/bash

echo "Creating OPAL_SIMULATOR image"

# Set default values
COMPSS_VERSION="3.2"
ORG_NAME="hp2c"
HP2C_VERSION="1.0"

# Check provided arguments
if [[ "${#}" -ge "1" ]]; then
    COMPSS_VERSION="${1}"
fi

if [[ "${#}" -ge "2" ]]; then
    ORG_NAME="${2}"
fi

if [[ "${#}" -ge "3" ]]; then
    HP2C_VERSION="${3}"
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

docker build -t ${ORG_NAME}/opal_simulator:${HP2C_VERSION} -f Dockerfile.opal_simulator ${SCRIPT_DIR}/../../components/

