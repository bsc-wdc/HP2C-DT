#!/usr/bin/env bash
source ../venv/bin/activate
nohup ./run_user_interface.sh --comm=bsc_subnet > ../logs/run_user_interface.log 2>&1 &
