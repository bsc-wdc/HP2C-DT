#!/bin/bash

echo "Creating SERVER image"

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

docker build -t ${ORG_NAME}/server:${HP2C_VERSION} -f Dockerfile.server ${SCRIPT_DIR}/../../components/
docker tag ${ORG_NAME}/server:${HP2C_VERSION} ${ORG_NAME}/server:latest
