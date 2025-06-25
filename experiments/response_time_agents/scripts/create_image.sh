#!/bin/bash
set -e  # Stop on error

if [ $# -ne 2 ] || ([ "$1" != "simple" ] && [ "$1" != "simple_external" ] && [ "$1" != "matmul" ] && ["$1" != "hp2c"]) || ([ "$2" != "trunk" ] && [ "$2" != "tempfixagents" ]); then
    echo "Usage: $0 <simple|simple_external|matmul|hp2c> <trunk|tempfixagents>"
    exit 1
fi

version="$1"
if [ "$version" == "matmul" ]; then
    version=""
fi

version_suffix=""
if [ -n "$version" ]; then
    version_suffix="_$version"
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Build the matmul project first
echo "Building matmul.jar with Maven..."
(cd "$SCRIPT_DIR/../matmul$version_suffix" && mvn clean package)

# Verify the JAR was created
MATMUL_JAR="$SCRIPT_DIR/../matmul$version_suffix/target/matmul.jar"
if [ ! -f "$MATMUL_JAR" ]; then
    echo "Error: matmul.jar was not created by Maven build!"
    exit 1
fi

# Define a temporary build directory
BUILD_DIR="./docker_build"
APP_DIR="$BUILD_DIR/app"

# Create the build directory and app subdirectory
rm -rf "$BUILD_DIR"
mkdir -p "$APP_DIR"

# Copy necessary files into the build context
cp "$MATMUL_JAR" "$APP_DIR/"
cp "$SCRIPT_DIR/single_cpu_project.xml" "$APP_DIR/"
cp "$SCRIPT_DIR/../../response_time/scripts/edge_project.xml" "$APP_DIR/"
cp "$SCRIPT_DIR/../../response_time/scripts/server_project.xml" "$APP_DIR/"

# Copy Dockerfile to build directory
cp "$SCRIPT_DIR/Dockerfile" "$BUILD_DIR/"

# Build the Docker image using the build context
echo "Building Docker image..."
docker build --build-arg COMPSs_VERSION="$2" -t hp2c/matmul${version_suffix}-image:"$2" "$BUILD_DIR"

echo "Docker image 'hp2c/matmul${version_suffix}-image:$2' created successfully"

# Remove the temporary build directory
rm -rf "$BUILD_DIR"
echo "Temporary build directory removed."
