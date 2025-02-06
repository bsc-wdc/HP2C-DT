#!/bin/bash

echo "Creating USER_INTERFACE image"

# Set default values
COMPSS_VERSION="trunk"
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

docker build -t ${ORG_NAME}/user_interface:${HP2C_VERSION} --build-arg="COMPSS_VERSION=${COMPSS_VERSION}" -f ${SCRIPT_DIR}/Dockerfile.user_interface ${SCRIPT_DIR}/../../components/userInterface/
docker tag ${ORG_NAME}/user_interface:${HP2C_VERSION} ${ORG_NAME}/user_interface:latest
