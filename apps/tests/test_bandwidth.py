import json
import os
import subprocess

import paramiko
from scp import SCPClient

# Constants
SSH_KEY = "/home/mauro/claves/hp2cdt-ncloud.pem" # chmod 600
BROKER_IP = "212.128.226.53"
REMOTE_USER = "ubuntu"


def main():
    time_steps = [1000, 100, 10, 1]
    window_frequencies = [1, 10, 100, 1000, 10000, 30000, 60000]

    script_dir = os.path.dirname(__file__)
    os.chdir(script_dir)

    for time_step in time_steps:
        for frequency in window_frequencies:
            if frequency < time_step:
                continue

            window_size = int(frequency / time_step)

            with open("edge_template.json", "r") as file:
                edge_json = json.load(file)
                edge_json["global-properties"]["window-size"] = window_size
                edge_json["devices"][0]["properties"]["amqp-agg-args"]["phasor-freq"] = frequency

            with open("../../deployments/test_bandwidth/setup/edge1.json", "w") as file:
                json.dump(edge_json, file)

            print(f"Updated JSON with window-size {window_size} and phasor-freq {frequency}")

            deploy_broker()
            deploy_server()
            deploy_opal_simulator_and_edge()
            subprocess.run("sleep 200")
            copy_metrics_from_server()
            copy_metrics_to_local()
            stop_broker()
            stop_server()


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
    if error:
        print(f"Error: {error}")
    return output


def deploy_broker():
    """Connect to broker and deploy docker."""
    client = connect_ssh()
    execute_ssh_command(client, "./hp2cdt/deployments/deploy_broker.sh &")
    client.close()


def stop_broker():
    """Connect to broker and stop hp2c_broker container."""
    client = connect_ssh()
    execute_ssh_command(client, "docker stop hp2c_broker &&"
                                "docker rm hp2c_broker")
    client.close()


def deploy_server():
    """Connect to server via broker and deploy server."""
    client = connect_ssh()
    execute_ssh_command(client,
                        "connect-server && "
                        "./hp2cdt/deployments/deploy_server.sh "
                        "--deployment_name=test_bandwidth --comm=bsc &")
    client.close()


def stop_server():
    """Connect to server and stop hp2c_server container."""
    client = connect_ssh()
    execute_ssh_command(client, "connect-server && "
                                "docker stop hp2c_server &&"
                                "docker rm hp2c_server")
    client.close()


def copy_metrics_from_server():
    """Copy execution0.csv from the server container to the remote machine."""
    client = connect_ssh()
    execute_ssh_command(client, ""
                                "connect-server && "
                                "docker cp hp2c_server:/home/ubuntu/metrics/execution0.csv "
                                "/home/ubuntu/execution0.csv")
    client.close()


def copy_metrics_to_local():
    """Copy execution0.csv from remote machine to local machine."""
    client = connect_ssh()
    scp = SCPClient(client.get_transport())
    scp.get("/home/ubuntu/execution0.csv",
            "/home/mauro/BSC/tests/local_metrics/execution0.csv")
    scp.close()
    client.close()


def deploy_opal_simulator_and_edge():
    subprocess.run("./../../deployments/deploy_opal_simulator.sh simple &")
    subprocess.run("./../../deployments/deploy_edges.sh "
                   "--deployment_name=test_bandwidth --comm=bsc &")

def stop_opal_simulator_and_edge():
    subprocess.run("docker stop hp2c_opal_simulator")
    subprocess.run("docker stop hp2c_edge1")


if __name__ == "__main__":
    main()
