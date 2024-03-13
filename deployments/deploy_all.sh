#!/bin/bash

DEPLOYMENT_NAME="testbed"
ORG_NAME="hp2c"
HP2C_VERSION="1.0"

if [ $# -ge 1 ]; then
  DEPLOYMENT_NAME=$1
fi
if [ $# -ge 2 ]; then
  ORG_NAME=$2
fi
if [ $# -ge 3 ]; then
  HP2C_VERSION=$3
fi

# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

custom_ip_address="172.29.128.1"

export DEPLOYMENT_NAME=$DEPLOYMENT_NAME
export ORG_NAME=$ORG_NAME
export HP2C_VERSION=$HP2C_VERSION
export CUSTOM_IP=$custom_ip_address
export LOCAL_IP=$ip_address

cd ../docker

docker-compose up --build
