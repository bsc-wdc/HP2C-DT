#!/bin/bash
set -e  # Stop on error

# Validate arguments
if [ $# -ne 1 ] || ([ "$1" != "edge" ] && [ "$1" != "server" ] && [ "$1" != "sequential" ]); then
    echo "Usage: $0 <edge|server|sequential>"
    exit 1
fi

MODE=$1
CONTAINER_NAME="matmul-$MODE"
IMAGE_NAME="hp2c/matmul-image"

# Function to handle cleanup
cleanup() {
    echo "Cleaning up..."
    docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
    docker rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
    exit 0
}

# Trap SIGINT (Ctrl+C) and call cleanup function
trap cleanup SIGINT

# Get the IPv4 address from wlp or eth interfaces
ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'wlp[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)

if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'eth[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi
if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'enxcc[0-9]+' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi
if [ -z "$ip_address" ]; then
    ip_address=$(ip addr show | grep -E 'inet\s' | grep -E 'ens3' | awk '{print $2}' | cut -d '/' -f 1 | head -n 1)
fi

# Set parameters based on mode
if [ "$MODE" == "edge" ]; then
    REST_PORT=46101
    COMM_PORT=46102
    PROJECT_FILE="/app/edge_project.xml"
elif [ "$MODE" == "server" ]; then
    REST_PORT=46201
    COMM_PORT=46202
    PROJECT_FILE="/app/server_project.xml"
elif [ "$MODE" == "sequential" ]; then
    REST_PORT=46301
    COMM_PORT=46302
    PROJECT_FILE="/app/single_cpu_project.xml"
fi

# Run the container with appropriate ports and command
echo "Starting $MODE container..."
docker run \
    -p $REST_PORT:$REST_PORT \
    -p $COMM_PORT:$COMM_PORT \
    --name "$CONTAINER_NAME" \
    -d "$IMAGE_NAME" \
    bash -c "compss_agent_start \
        --hostname=$ip_address \
        --classpath=/app/matmul.jar \
        --log_dir=/tmp/$MODE \
        --rest_port=$REST_PORT \
        --comm_port=$COMM_PORT \
        --project=$PROJECT_FILE && \
        tail -f /dev/null"

echo "Container '$CONTAINER_NAME' running in $MODE mode"
echo "  - REST API port: $REST_PORT"
echo "  - Communication port: $COMM_PORT"
echo "  - Using project file: $PROJECT_FILE"
echo "  - Log dir: /tmp/$MODE"
echo "Press Ctrl+C to stop the container"

# Attach to logs and wait for termination
docker logs -f "$CONTAINER_NAME" &

# Wait indefinitely until interrupted
wait

# Regular cleanup if logs stop naturally
cleanup
