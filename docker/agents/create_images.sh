#!/bin/bash
echo  "Creating AGENTS images"

if [[ "${#}" -lt "1" ]]; then
    COMPSS_VERSION="trunk"
else 
    COMPSS_VERSION="${1}"
fi
  


# Setting up trap to clear environment
on_exit(){
    if [ -n "${tmpfile}" ]; then
        rm "${tmpfile}"
    fi
}
trap 'on_exit' EXIT

tmpfile=$(mktemp ${PWD}/agents-XXXXXX.yml)

echo "
services:
  agents_manager:
    build:
      context: .
      dockerfile: Dockerfile.agents
      target: agents_manager
      args:
        COMPSS_VERSION: ${COMPSS_VERSION}
    image: compss/agents_manager:${COMPSS_VERSION}
  compss-agent-java:
    build:
      context: .
      dockerfile: Dockerfile.agents
      target: java_agent
      args:
        COMPSS_VERSION: ${COMPSS_VERSION}
    image: compss/java_agents:${COMPSS_VERSION}
  compss-agent-python:
    build:
      context: .
      dockerfile: Dockerfile.agents
      target: python_agent
      args:
        COMPSS_VERSION: ${COMPSS_VERSION}
    image: compss/python_agents:${COMPSS_VERSION}
" > ${tmpfile}
docker compose -f "${tmpfile}" build
