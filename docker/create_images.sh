#!/bin/bash
HP2C_VERSION=1.0
COMPSS_VERSION=3.2

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ $# -eq 1 ]; then
  IMAGE_NAME=$1/edge
else
  IMAGE_NAME=hp2c/edge
fi

cd ${SCRIPT_DIR}/agents
./create_images.sh "${COMPSS_VERSION}"
cd ..

docker build -t ${IMAGE_NAME}:${HP2C_VERSION} --build-arg="COMPSS_VERSION=${COMPSS_VERSION}" -f ${SCRIPT_DIR}/Dockerfile.edge ${SCRIPT_DIR}/../components/
docker tag ${IMAGE_NAME}:${HP2C_VERSION} ${IMAGE_NAME}:latest
