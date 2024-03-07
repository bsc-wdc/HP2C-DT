#!/bin/bash

./init_dashboard.sh --deployment_dir /data/edge
python3 ../manage.py migrate --noinput
python3 ../manage.py collectstatic --noinput
python3 ../manage.py runserver 0.0.0.0:80