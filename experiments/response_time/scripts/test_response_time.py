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
    stop_opal_simulator_and_edge("test_response_time")
    print("Cleanup done.")

# Handle CTRL+C (SIGINT)
def signal_handler(sig, frame):
    print("\nCTRL+C detected! Cleaning up before exit...")
    cleanup()
    sys.exit(1)

signal.signal(signal.SIGINT, signal_handler)


def wait_for_log_lines(log_file, min_lines=15, test_host="edge"):
    """Wait for log lines, handling remote execution when test_host='server'."""
    print(f"Waiting until log has at least {min_lines} lines...")

    # Remote execution on broker
    client = connect_ssh()
    try:
        while True:
            # Get line count from broker
            count_cmd = "docker logs hp2c_edge1 2>&1 | grep -ic 'Timestamp difference'"
            output = execute_ssh_command(client, count_cmd)
            remote_count = int(output.strip() or 0)  # Handle empty output

            print(f"Current count: {remote_count}")

            if remote_count >= min_lines:
                # Fetch logs and write to local file
                logs_cmd = "docker logs hp2c_edge1 2>&1 | grep -i 'Timestamp difference'"
                logs = execute_ssh_command(client, logs_cmd)
                with open(log_file, "w") as f:
                    f.write(logs)
                break

            time.sleep(1)
    finally:
        client.close()


def main(args):
    msizes = [1, 10, 100, 1000]
    bsizes = [1, 10, 100, 1000]
    time_steps = [1000, 10000]
    dirname = os.path.dirname(__file__)
    test_host = "edge"
    if len(args) > 1 and args[1] == "server":
        test_host = "server"

    file_path = "/home/ubuntu/hp2cdt/deployments/test_response_time/setup/edge1.json"

    for i in range(2):
        # run first iteration with workflow, and second iteration sequentially
        edge_json = run_simulations(bsizes, dirname, file_path, i, msizes,
                                    test_host, time_steps)

        # Run sequentially: delete the entry workflow from matmul
        del edge_json["funcs"][0]["type"]
        if test_host == "server": # execute only for workflow
            break

    print("END")


def run_simulations(bsizes, dirname, file_path, i, msizes, test_host,
                    time_steps):
    for time_step in time_steps:
        for msize in msizes:
            for bsize in bsizes:
                if test_host == "edge":
                    if msize > 1 or bsize > 100:
                        continue

                if msize == 0 and bsize > 1:
                    continue

                mode = "wf" if i == 0 else "seq"

                os.chdir(dirname)

                with open("edge_template_matmul.json", "r") as file:
                    edge_json = json.load(file)
                    edge_json["funcs"][0]["parameters"]["other"][
                        "msize"] = msize
                    edge_json["funcs"][0]["parameters"]["other"][
                        "bsize"] = bsize
                    if test_host == "edge":
                        edge_json["funcs"][0][
                            "method-name"] = "es.bsc.hp2c.common.funcs.MatMulEdge"
                    else:
                        edge_json["funcs"][0][
                            "method-name"] = "es.bsc.hp2c.common.funcs.MatMulServer"
                    if mode == "seq":
                        del edge_json["funcs"][0]["type"]

                # Write inside broker
                client = connect_ssh()
                try:
                    print(f"Writing file in {file_path}")
                    remote_dir = os.path.dirname(file_path)
                    execute_ssh_command(client, f"mkdir -p {remote_dir}")
                    json_str = json.dumps(edge_json, indent=2)
                    sftp = client.open_sftp()
                    with sftp.file(file_path, 'w') as remote_file:
                        remote_file.write(json_str)
                    sftp.close()
                except Exception as e:
                    print(f"Error writing in broker: {e}")
                finally:
                    client.close()


                print(f"Updated JSON with msize={msize}, bsize={bsize} "
                      f"(time_step {time_step})")

                print("Deploying opal simulator and edge...")
                deploy_opal_simulator_and_edge(time_step, "test_response_time")
                print("Deploying broker...")
                deploy_broker()
                print("Deploying server...")
                deploy_server("test_response_time")

                log_file = (f"{dirname}/../results/raw/ts{time_step}_m{msize}"
                            f"_b{bsize}_{mode}_{test_host}.log")
                log_path = os.path.dirname(log_file)
                os.makedirs(log_path, exist_ok=True)

                # Wait until the log file has at least 15 lines
                wait_for_log_lines(log_file, 15)

                cleanup()
                print("Iteration end")
                print("///////////////////////////////////////////\n")

    return edge_json


def add_resources():
    client = connect_ssh()
    command = (
        "docker exec hp2c_edge1 compss_agent_add_resources "
        "--agent_node=127.0.0.1 "
        "--agent_port=46101 "
        "--cpu=4 "
        "212.128.226.53 Port=8002"
    )
    print("Adding COMPSs resources directly...")
    execute_ssh_command(client, command)

    client.close()

if __name__ == "__main__":
    args = sys.argv
    main(args)
