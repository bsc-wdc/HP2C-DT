#!/bin/bash
HP2C_VERSION=1.0
COMPSS_VERSION=3.2

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd ${SCRIPT_DIR}/agents
./create_images.sh "${COMPSS_VERSION}"
cd ..

docker build -t maaurogl/edge:${HP2C_VERSION} --build-arg="COMPSS_VERSION=${COMPSS_VERSION}" -f ${SCRIPT_DIR}/Dockerfile.edge ${SCRIPT_DIR}/../components/edge
docker tag maaurogl/edge:${HP2C_VERSION} maaurogl/edge:latest
