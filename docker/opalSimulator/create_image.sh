SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PATH_TO_CONTEXT="$SCRIPT_DIR/../../components/opalSimulator"

docker build -t opal-client:latest -f ${SCRIPT_DIR}/Dockerfile.opal ${PATH_TO_CONTEXT}
