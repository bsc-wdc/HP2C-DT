#!/bin/bash

deployment_name="testbed"

if [ $# -eq 1 ]; then
  deployment_name=$1
fi

deployment_dir="../../../deployments/${deployment_name}/setup"
docker=0

if [ -f /.dockerenv ]; then
    deployment_dir="/data/edge"
    docker=1
fi

if [ $docker -eq 0 ]; then
    cd ..
    python3 manage.py migrate --run-syncdb
    python3 manage.py collectstatic --noinput
    python3 manage.py runserver 0.0.0.0:8000
else
    python3 ../manage.py migrate --run-syncdb
    python3 ../manage.py collectstatic --noinput
    python3 ../manage.py runserver 0.0.0.0:8000
fi