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

# Execute create_images on each sub-directory
original_dir=$(pwd)
for directory in */; do
    cd "$directory" || exit
    ./create_images.sh "${COMPSS_VERSION}" "${ORG_NAME}" "${HP2C_VERSION}"
    cd "$original_dir" || exit
done