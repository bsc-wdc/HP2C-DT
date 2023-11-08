#!/bin/bash

echo "Creating RabbitMQ broker image"

HP2C_VERSION=1.0
COMPSS_VERSION=3.2
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "current dir is $SCRIPT_DIR"
echo "using ${SCRIPT_DIR}/../../components/broker"

docker build -t hp2c/broker:${HP2C_VERSION} -f Dockerfile.broker ${SCRIPT_DIR}/../../components/broker
docker tag hp2c/broker:${HP2C_VERSION} hp2c/broker:latest

