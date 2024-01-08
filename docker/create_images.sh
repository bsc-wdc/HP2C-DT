#!/bin/bash
HP2C_VERSION=1.0
COMPSS_VERSION=3.2
ORG_NAME="hp2c"

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ $# -eq 1 ]; then
  IMAGE_NAME=$1/edge
else
  IMAGE_NAME=${ORG_NAME}/edge
fi

cd ${SCRIPT_DIR}/agents
./create_images.sh "${COMPSS_VERSION}"
cd ..

cd ${SCRIPT_DIR}/server
./create_images.sh "${COMPSS_VERSION}" "${ORG_NAME}" "${HP2C_VERSION}"
cd ..

cd ${SCRIPT_DIR}/broker
./create_images.sh "${COMPSS_VERSION}" "${ORG_NAME}" "${HP2C_VERSION}"
cd ..

docker build -t ${IMAGE_NAME}:${HP2C_VERSION} --build-arg="COMPSS_VERSION=${COMPSS_VERSION}" -f ${SCRIPT_DIR}/Dockerfile.edge ${SCRIPT_DIR}/../components/
docker tag ${IMAGE_NAME}:${HP2C_VERSION} ${IMAGE_NAME}:latest
