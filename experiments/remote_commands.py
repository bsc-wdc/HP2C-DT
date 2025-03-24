import os
import subprocess

import paramiko
from scp import SCPClient


# Constants
# Insert the path to the local ncloud.pem file
SSH_KEY = "/home/mauro/claves/hp2cdt-ncloud.pem" # chmod 600
BROKER_IP = "212.128.226.53"
REMOTE_USER = "ubuntu"

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


def deploy_server(test_name):

    """Connect to the server via broker and deploy it in the background."""
    server_client, broker_client = connect_to_server()

    # Run server deployment in background with nohup
    execute_ssh_command(server_client,
                        "nohup ./hp2cdt/deployments/deploy_server.sh "
                        f"-m --deployment_name={test_name} --comm=bsc_subnet "
                        f">/dev/null 2>&1 &")
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


def copy_metrics_to_local(time_step, window_size, dirname, aggregate):
    """Copy execution0.csv from the remote broker machine to the local machine."""
    server_client, broker_client = connect_to_server()

    local_path = f"{dirname}/../results/raw/ts{time_step}_ws{window_size}.csv"

    if aggregate == "all":
        local_path = f"{dirname}/../results/raw/all_ts{time_step}_ws{window_size}.csv"

    # Ensure local directory exists before copying
    os.makedirs(os.path.dirname(local_path), exist_ok=True)

    # Copy the file from the remote server to the local machine (overwrites if exists)
    scp = SCPClient(server_client.get_transport())
    scp.get("/home/ubuntu/test_bandwidth/execution0.csv", local_path)

    scp.close()
    server_client.close()
    broker_client.close()


def deploy_opal_simulator_and_edge(time_step, test_name):
    flag = ""
    if test_name == "test_response_time":
        flag = "-t"

        client = connect_ssh()  # connect to broker machine
        print("Deploying opalsim")

        opal_command = (
            f"nohup ./hp2cdt/deployments/deploy_opal_simulator.sh "
            f"--deployment_name=simple --time_step={time_step} "
            f">/dev/null 2>&1 &"
        )
        execute_ssh_command(client, opal_command)
        print("Deploying edge")

        edge_command = (
            f"nohup ./hp2cdt/deployments/deploy_edges.sh "
            f"--deployment_name={test_name} {flag} --comm=bsc_subnet "
            f">/dev/null 2>&1 &"
        )
        execute_ssh_command(client, edge_command)

        client.close()

    else:
        dirname = os.path.dirname(__file__)
        os.chdir(f"{dirname}/../deployments")

        subprocess.run("nohup ./deploy_opal_simulator.sh "
                       f"--deployment_name=simple --time_step={time_step} "
                       f">/dev/null 2>&1 &", shell=True)
        subprocess.run(f"nohup ./deploy_edges.sh "
                       f"--deployment_name={test_name} {flag} --comm=bsc >/dev/null 2>&1 &", shell=True)


def stop_opal_simulator_and_edge(test_name):
    if test_name == "test_response_time":
        client = connect_ssh()
        execute_ssh_command(client,
                            "docker stop hp2c_opal_simulator && docker rm hp2c_opal_simulator")
        execute_ssh_command(client,
                            "docker stop hp2c_edge1 && docker rm hp2c_edge1")
        client.close()
    else:
        subprocess.run("docker stop hp2c_opal_simulator", shell=True)
        subprocess.run("docker stop hp2c_edge1", shell=True)
