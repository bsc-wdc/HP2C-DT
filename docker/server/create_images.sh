#!/bin/bash

echo "Creating SERVER image"

if [[ "${#}" -lt "1" ]]; then
    VERSION_TAG="3.2"
else 
    VERSION_TAG="${1}"
fi

IMAGE_NAME="hp2c"

HP2C_VERSION=1.0
COMPSS_VERSION=3.2
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

docker build -t ${IMAGE_NAME}/server:${HP2C_VERSION} -f Dockerfile.server ${SCRIPT_DIR}/../../components/
docker tag ${IMAGE_NAME}/server:${HP2C_VERSION} ${IMAGE_NAME}/server:latest
