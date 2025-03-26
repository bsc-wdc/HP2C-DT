#!/bin/bash
set -e  # Stop on error

# Define a temporary build directory
BUILD_DIR="./docker_build"
APP_DIR="$BUILD_DIR/app"

# Create the build directory and app subdirectory
rm -rf "$BUILD_DIR"
mkdir -p "$APP_DIR"

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Copy necessary files into the build context
cp $SCRIPT_DIR/../matmul/target/matmul.jar "$APP_DIR/"
cp $SCRIPT_DIR/../../response_time/scripts/edge_project.xml "$APP_DIR/"
cp $SCRIPT_DIR/../../response_time/scripts/server_project.xml "$APP_DIR/"

# Create Dockerfile with curl and jq installation
cat > "$BUILD_DIR/Dockerfile" <<EOF
FROM compss/compss:trunk

# Install curl and jq
RUN apt-get update && \\
    apt-get install -y --no-install-recommends curl jq && \\
    rm -rf /var/lib/apt/lists/*

# Copy application files
COPY app/ /app/
WORKDIR /app

# Default command (can be overridden in deploy)
CMD ["tail", "-f", "/dev/null"]
EOF

# Build the Docker image using the build context
docker build -t hp2c/matmul-image "$BUILD_DIR"

echo "Docker image 'hp2c/matmul-image' created successfully with curl and jq installed!"

# Remove the temporary build directory
rm -rf "$BUILD_DIR"
echo "Temporary build directory removed."