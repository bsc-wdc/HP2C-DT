import json
import sys
import os
import signal
import atexit
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



def main(args):
    time_steps = [1, 10, 100, 1000, 10000]
    window_frequencies = [1, 10, 100, 1000, 10000]
    dirname = os.path.dirname(__file__)
    file_path = "/home/mauro/BSC/hp2cdt/deployments/test_bandwidth/setup/edge1.json"
    dir_path = os.path.dirname(file_path)
    os.makedirs(dir_path, exist_ok=True)

    for time_step in time_steps:
        for frequency in window_frequencies:
            if len(args) > 1 and args[1] == "all":
                if frequency < time_step:
                    continue
            else:
                if frequency <= time_step:
                    continue

            window_size = int(frequency / time_step)
            os.chdir(dirname)
            template_path = "edge_template_phasor.json"
            if len(args) > 1 and args[1] == "all":
                template_path = "edge_template_all.json"
            with open(template_path, "r") as file:
                edge_json = json.load(file)
                edge_json["global-properties"]["window-size"] = window_size
                edge_json["devices"][0]["properties"]["amqp-frequency"] = frequency

            with open(file_path, "w") as file:
                json.dump(edge_json, file)

            print(f"Updated JSON with window-size {window_size} and frequency {frequency} (time_step {time_step})")

            print("Deploying opal simulator and edge...")
            deploy_opal_simulator_and_edge(time_step, "test_bandwidth")
            print("Deploying broker...")
            deploy_broker()
            print("Deploying server...")
            deploy_server("test_bandwidth")
            print("Sleep...")
            subprocess.run("sleep 20", shell=True)
            print("Copying metrics from server...")
            copy_metrics_from_server()
            print("Copying metrics locally...")
            aggregate = "phasor"
            if len(args) > 1 and args[1] == "all":
                aggregate = "all"
            copy_metrics_to_local(time_step, window_size, dirname, aggregate)
            cleanup()
            print("Iteration end")
            print("///////////////////////////////////////////")
            print("///////////////////////////////////////////")
            print()
    os.rmdir(dir_path)
    print("END")


if __name__ == "__main__":
    args = sys.argv
    main(args)
