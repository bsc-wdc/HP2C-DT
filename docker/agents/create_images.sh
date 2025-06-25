#!/bin/bash
echo  "Creating AGENTS images"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Argument parsing
if [[ "${#}" -lt "1" ]]; then
    COMPSS_VERSION="trunk"
else 
    COMPSS_VERSION="${1}"
fi

# Check version to use a default or custom COMPSs source
if [ "$COMPSS_VERSION" = "trunk" ]; then
    # Use local installation to copy local COMPSs files
    echo "WARNING: using local COMPSs install to build agents image"
    echo "Make sure compss/compss:$COMPSS_VERSION image is built locally!"
    rsync -cra /opt/COMPSs/ ${SCRIPT_DIR}/COMPSs/
    DOCKERFILE="Dockerfile.agents.DEVEL"
else
    echo "Using official COMPSs image"
    DOCKERFILE="Dockerfile.agents"
fi

# Setting up trap to clear environment
on_exit(){
    # Delete temporary files
    if [ -n "${tmpfile}" ]; then
        rm "${tmpfile}"
    fi
    if [ "$COMPSS_VERSION" = "trunk" ]; then
        echo "REMOVING TEMP DIR!"
        rm -r ${SCRIPT_DIR}/COMPSs/
    fi
}
trap 'on_exit' EXIT

tmpfile=$(mktemp ${PWD}/agents-XXXXXX.yml)

cp ../../components/common/src/main/resources/log4j2.xml ./log4j2.xml

echo "
services:
  agents_manager:
    build:
      context: .
      dockerfile: ${DOCKERFILE}
      target: agents_manager
      args:
        COMPSS_VERSION: ${COMPSS_VERSION}
    image: compss/agents_manager:${COMPSS_VERSION}
  compss-agent-java:
    build:
      context: .
      dockerfile: ${DOCKERFILE}
      target: java_agent
      args:
        COMPSS_VERSION: ${COMPSS_VERSION}
    image: compss/java_agents:${COMPSS_VERSION}
  compss-agent-python:
    build:
      context: .
      dockerfile: ${DOCKERFILE}
      target: python_agent
      args:
        COMPSS_VERSION: ${COMPSS_VERSION}
    image: compss/python_agents:${COMPSS_VERSION}
" > ${tmpfile}
docker compose -f "${tmpfile}" build

rm ./log4j2.xml
