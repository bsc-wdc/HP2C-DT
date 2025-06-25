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

# Copy local compilation of the COMPSs engine
TMP_DIR="${SCRIPT_DIR}/../../components/tmp"
mkdir -p ${TMP_DIR}
touch "${TMP_DIR}/.keep"  # Avoid copying errors in Dockerfile if directory is empty
if [ "$COMPSS_VERSION" = "trunk" ]; then
    echo "WARNING: Copying compss-engine.jar from /opt/COMPSs/"
    echo "Make sure COMPSs is correctly installed locally!"
    cp /opt/COMPSs/Runtime/compss-engine.jar ${TMP_DIR}/compss-engine.jar
fi

docker build \
    -t ${ORG_NAME}/server:${HP2C_VERSION} \
    --build-arg="COMPSS_VERSION=${COMPSS_VERSION}" \
    -f Dockerfile.server \
    ${SCRIPT_DIR}/../../components/
docker tag ${ORG_NAME}/server:${HP2C_VERSION} ${ORG_NAME}/server:latest

# Remove temporary files
if [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
    echo "Deleted: $TMP_DIR"
fi
