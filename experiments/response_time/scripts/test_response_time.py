import json
import os
import sys
import signal
import atexit
import time
import subprocess

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))
from experiments.remote_commands import *

# Ensure cleanup on exit (CTRL+C or normal exit)
def cleanup():
    print("Stopping broker...")
    stop_broker()
    print("Stopping server...")
    stop_server()
    print("Stopping opal simulator and edge...")
    stop_opal_simulator_and_edge()
    print("Cleanup done.")

# Register cleanup for normal exit
atexit.register(cleanup)

# Handle CTRL+C (SIGINT)
def signal_handler(sig, frame):
    print("\nCTRL+C detected! Cleaning up before exit...")
    cleanup()
    sys.exit(1)

signal.signal(signal.SIGINT, signal_handler)

def wait_for_log_lines(log_file, min_lines=15):
    """Continuously updates the log file and waits until it has at least `min_lines` lines."""
    print(f"Waiting until {log_file} has at least {min_lines} lines...")

    # Ensure the log file exists before starting
    if not os.path.exists(log_file):
        open(log_file, "w").close()  # Create an empty file

    while True:
        # Update the log file with the latest entries
        subprocess.run(
            f"docker logs hp2c_edge1 | grep -i \"Timestamp difference\" > {log_file}",
            shell=True
        )

        # Count the lines in the log file
        with open(log_file, "r") as f:
            line_count = sum(1 for _ in f)

        print(f"Current line count: {line_count}")

        if line_count >= min_lines:
            print(f"{log_file} reached {min_lines} lines! Continuing execution...")
            break

        time.sleep(1)  # Check every second

def main(args):
    matrix_sizes = [0, 1, 10, 100, 1000, 10000]
    time_steps = [1000, 10000]
    dirname = os.path.dirname(__file__)
    file_path = "/home/mauro/BSC/hp2cdt/deployments/test_response_time/setup/edge1.json"
    dir_path = os.path.dirname(file_path)
    os.makedirs(dir_path, exist_ok=True)

    for i in range(2):
        for time_step in time_steps:
            for n in matrix_sizes:

                os.chdir(dirname)
                template_path = "edge_template_matmul.json"

                with open(template_path, "r") as file:
                    edge_json = json.load(file)
                    edge_json["funcs"][0]["parameters"]["other"]["n"] = n

                with open(file_path, "w") as file:
                    json.dump(edge_json, file, indent=2)

                print(f"Updated JSON with n={n} (time_step {time_step})")

                print("Deploying opal simulator and edge...")
                deploy_opal_simulator_and_edge(time_step, "test_response_time")
                print("Deploying broker...")
                deploy_broker()
                print("Deploying server...")
                deploy_server("test_response_time")

                mode = "wf" if i == 0 else "seq"
                log_file = f"{dirname}/../results/ts{time_step}_n{n}_{mode}.log"
                log_path = os.path.dirname(log_file)
                os.makedirs(log_path, exist_ok=True)

                # Wait until the log file has at least 15 lines
                wait_for_log_lines(log_file, 200)

                cleanup()
                print("Iteration end")
                print("///////////////////////////////////////////\n")

        # Run sequentially: delete the entry workflow from matmul
        del edge_json["funcs"][0]["type"]

        with open(file_path, "w") as file:
            json.dump(edge_json, file)

    os.remove(file_path)  # Remove file at the end
    print("END")

if __name__ == "__main__":
    args = sys.argv
    main(args)
