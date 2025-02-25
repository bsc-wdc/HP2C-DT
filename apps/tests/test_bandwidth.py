import json
import os
import subprocess
import sys
import time

import paramiko
from scp import SCPClient

# Constants
SSH_KEY = "/home/mauro/claves/hp2cdt-ncloud.pem" # chmod 600
BROKER_IP = "212.128.226.53"
REMOTE_USER = "ubuntu"


def main(args):
    time_steps = [1, 10, 100, 1000]
    window_frequencies = [1, 10, 100, 1000, 10000, 20000]
    script_dir = os.path.dirname(__file__)

    for time_step in time_steps:
        for frequency in window_frequencies:
            if (frequency) <= time_step:
                continue

            window_size = int(frequency/ time_step)
            os.chdir(script_dir)
            template_path = "edge_template_phasor.json"
            if args is not None and args[1] == "all":
                template_path = "edge_template_all.json"
            with open(template_path, "r") as file:
                edge_json = json.load(file)
                edge_json["global-properties"]["window-size"] = window_size
                edge_json["devices"][0]["properties"]["amqp-frequency"] = frequency

            with open("../../deployments/test_bandwidth/setup/edge1.json", "w") as file:
                json.dump(edge_json, file)

            print(f"Updated JSON with window-size {window_size} and frequency {frequency} (time_step {time_step})")

            print("Deploying opal simulator and edge...")
            deploy_opal_simulator_and_edge(time_step)
            print("Deploying broker...")
            deploy_broker()
            print("Deploying server...")
            deploy_server()
            print("Sleep...")
            subprocess.run("sleep 200", shell=True)
            print("Copying metrics from server...")
            copy_metrics_from_server()
            print("Copying metrics locally...")
            copy_metrics_to_local(time_step, window_size)
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

    print("END")


def connect_ssh():
    """Establish an SSH connection."""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(BROKER_IP, username=REMOTE_USER, key_filename=SSH_KEY)
    return client


def execute_ssh_command(client, command):
    """Execute a command over SSH."""
    stdin, stdout, stderr = client.exec_command(command)
    output = stdout.read().decode()
    error = stderr.read().decode()
    print(f"STDOUT: (command {command})")
    print(output)
    if error:
        print(f"STDERR: (command {command})")
        print(error)
    print("-----------------------")
    print()
    return output


def deploy_broker():
    """Connect to broker and deploy docker."""
    client = connect_ssh()
    full_command = "nohup ./hp2cdt/deployments/deploy_broker.sh --comm=bsc_subnet >/dev/null 2>&1 &"
    execute_ssh_command(client, full_command)
    client.close()


def stop_broker():
    """Connect to broker and stop hp2c_broker container."""
    client = connect_ssh()
    execute_ssh_command(client, "docker stop hp2c_broker && "
                                "docker rm hp2c_broker")
    client.close()


def deploy_server():
    """Connect to the server via broker and deploy it in the background."""
    server_client, broker_client = connect_to_server()

    # Run server deployment in background with nohup
    execute_ssh_command(server_client,
                        "nohup ./hp2cdt/deployments/deploy_server.sh "
                        "-m --deployment_name=test_bandwidth --comm=bsc_subnet "
                        ">/dev/null 2>&1 &")
    server_client.close()
    broker_client.close()


def connect_to_server():
    """Connect to the broker, then establish an SSH connection to the server through the broker."""
    broker_client = connect_ssh()  # Connect to the broker

    # Get the SSH transport from the broker
    transport = broker_client.get_transport()
    dest_addr = ("192.168.0.203", 22)  # Target server IP
    local_addr = ("127.0.0.1", 0)
    channel = transport.open_channel("direct-tcpip", dest_addr, local_addr)

    # Create a second SSH client to connect from broker to server
    server_client = paramiko.SSHClient()
    server_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    server_client.connect("192.168.0.203", username="ubuntu", key_filename=SSH_KEY, sock=channel)

    return server_client, broker_client


def stop_server():
    """Connect to the server via broker and stop the hp2c_server container."""
    server_client, broker_client = connect_to_server()
    execute_ssh_command(server_client, "docker stop hp2c_server && docker rm hp2c_server")
    server_client.close()
    broker_client.close()


def copy_metrics_from_server():
    """Copy execution0.csv from the server container to the remote machine via broker."""
    server_client, broker_client = connect_to_server()

    # Ensure the destination directory exists on the server
    execute_ssh_command(server_client, "mkdir -p /home/ubuntu/test_bandwidth")

    # Copy file from container to server's filesystem (overwrites if exists)
    execute_ssh_command(server_client,
                        "docker cp hp2c_server:/metrics/execution0.csv /home/ubuntu/test_bandwidth/execution0.csv")

    server_client.close()
    broker_client.close()


def copy_metrics_to_local(time_step, window_size):
    """Copy execution0.csv from the remote broker machine to the local machine."""
    server_client, broker_client = connect_to_server()

    local_path = f"/home/mauro/BSC/tests/test_bandwidth/ts{time_step}_ws{window_size}.csv"

    if args is not None and args[1] == "all":
        local_path = f"/home/mauro/BSC/tests/test_bandwidth/all_ts{time_step}_ws{window_size}.csv"

    # Ensure local directory exists before copying
    os.makedirs(os.path.dirname(local_path), exist_ok=True)

    # Copy the file from the remote server to the local machine (overwrites if exists)
    scp = SCPClient(server_client.get_transport())
    scp.get("/home/ubuntu/test_bandwidth/execution0.csv", local_path)

    scp.close()
    server_client.close()
    broker_client.close()


def deploy_opal_simulator_and_edge(time_step):
    os.chdir("../../deployments")

    subprocess.run("nohup ./deploy_opal_simulator.sh "
                   f"--deployment_name=simple --time_step={time_step} "
                   f">/dev/null 2>&1 &", shell=True)
    subprocess.run("nohup ./deploy_edges.sh "
                   "--deployment_name=test_bandwidth --comm=bsc >/dev/null 2>&1 &", shell=True)

def stop_opal_simulator_and_edge():
    subprocess.run("docker stop hp2c_opal_simulator", shell=True)
    subprocess.run("docker stop hp2c_edge1", shell=True)


if __name__ == "__main__":
    args = sys.argv
    main(args)
