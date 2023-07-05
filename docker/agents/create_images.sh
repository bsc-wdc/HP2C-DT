#!/bin/bash
echo  "Creating AGENTS images"

if [[ "${#}" -lt "1" ]]; then
    VERSION_TAG="3.2"
else 
    VERSION_TAG="${1}"
fi
  


# Setting up trap to clear environment
on_exit(){
    if [ -n "${tmpfile}" ]; then
        rm "${tmpfile}"
    fi
}
trap 'on_exit' EXIT

tmpfile=$(mktemp ${PWD}/agents-XXXXXX.yml)

echo "version: '3.8'
services:
  agents_manager:
    build:
      context: .
      dockerfile: Dockerfile.agents
      target: agents_manager
    image: compss/agents_manager:${VERSION_TAG}
  compss-agent-java:
    build:
      context: .
      dockerfile: Dockerfile.agents
      target: java_agent
    image: compss/java_agents:${VERSION_TAG}
  compss-agent-python:
    build:
      context: .
      dockerfile: Dockerfile.agents
      target: python_agent
    image: compss/python_agents:${VERSION_TAG}
" > ${tmpfile}
docker-compose -f "${tmpfile}" build
