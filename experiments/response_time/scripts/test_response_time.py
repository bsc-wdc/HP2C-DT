import json
import os.path
import sys
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")))

from experiments.remote_commands import *


def main(args):
    matrix_sizes = [0, 1, 10, 100, 1000, 10000] # size of the square matrix (nxn)
    time_steps = [1, 10, 100, 1000, 10000]
    dirname = os.path.dirname(__file__)
    file_path = "/home/mauro/BSC/hp2cdt/deployments/test_response_time/setup/edge1.json"
    dir_path = os.path.dirname(file_path)

    os.makedirs(dir_path, exist_ok=True)

    for time_step in time_steps:
        for n in matrix_sizes:
            edge_execution = True
            if len(args) > 1 and args[1] == "server":
                edge_execution = False

            os.chdir(dirname)
            template_path = "edge_template_matmul.json"

            with open(template_path, "r") as file:
                edge_json = json.load(file)
                edge_json["funcs"][0]["parameters"]["other"]["n"] = n

            with open(file_path, "w") as file:
                json.dump(edge_json, file)

            print(f"Updated JSON with n={n} (time_step {time_step})")

            print("Deploying opal simulator and edge...")
            deploy_opal_simulator_and_edge(time_step, "test_response_time")
            print("Deploying broker...")
            deploy_broker()
            print("Deploying server...")
            if edge_execution:
                deploy_server("-e")
            else:
                deploy_server("-s")
            print("Sleep...")
            subprocess.run("sleep 200", shell=True)
            print("Stopping broker...")
            stop_broker()
            print("Stopping server...")
            stop_server()
            print("Stopping opal simulator and edge...")
            stop_opal_simulator_and_edge()
            print("Iteration end")
            print("///////////////////////////////////////////")
            print("///////////////////////////////////////////")
            print()
    os.rmdir(file_path)
    print("END")


if __name__ == "__main__":
    args = sys.argv
    main(args)
