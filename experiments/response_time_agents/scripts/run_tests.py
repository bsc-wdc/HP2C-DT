import signal
import sys
import paramiko
import time
import os
import threading

# SSH Configuration
SSH_KEY = "/home/mauro/claves/hp2cdt-ncloud.pem"
BROKER_IP = "212.128.226.53"
SERVER_IP = "192.168.0.203"
REMOTE_USER = "ubuntu"

msizes = [1, 2, 4, 8, 16, 32, 64]
bsizes = [1, 2, 4, 8, 16, 32, 64]

# Global variables for logging
log_data = []
log_lock = threading.Lock()
stop_logging = False


def connect_ssh(ip):
    """Establishes an SSH connection to the specified machine."""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(ip, username=REMOTE_USER, key_filename=SSH_KEY)
    return client


def execute_ssh_command(client, command, show_output=True):
    """Executes a command via SSH and returns its output."""
    stdin, stdout, stderr = client.exec_command(command)
    output = stdout.read().decode()
    error = stderr.read().decode()

    if show_output:
        print(f"Command: {command}")
        print(f"STDOUT:\n{output}")
        if error:
            print(f"STDERR:\n{error}")
        print("--------------------------------------")

    return output


def connect_to_server_through_broker():
    """Connects to the server through the broker."""
    broker_client = connect_ssh(BROKER_IP)

    transport = broker_client.get_transport()
    channel = transport.open_channel("direct-tcpip", (SERVER_IP, 22),
                                     ("127.0.0.1", 0))

    server_client = paramiko.SSHClient()
    server_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    server_client.connect(SERVER_IP, username=REMOTE_USER,
                          key_filename=SSH_KEY, sock=channel)

    return server_client, broker_client


def monitor_edge_log(broker_client, results_file):
    """Monitors the edge log and saves execution times."""
    log_entries = set()

    while True:
        output = execute_ssh_command(broker_client, "cat /tmp/edge.log", False)
        new_entries = [line for line in output.split("\n") if
                       "App completed after" in line and line not in log_entries]

        if new_entries:
            with open(results_file, 'a') as f:
                for line in new_entries:
                    log_entries.add(line)
                    f.write(line + '\n')
                    print(f"Logged: {line}")

        if len(log_entries) >= 5:
            break

        time.sleep(5)

    print(f"Saved {len(log_entries)} execution times to {results_file}")


def cleanup(server_client, broker_client):
    """Stops containers and kills deployment scripts."""
    print("Stopping deployment scripts and containers...")
    execute_ssh_command(broker_client,
                        "docker stop matmul-edge && docker rm matmul-edge",
                        False)
    execute_ssh_command(server_client,
                        "docker stop matmul-server && docker rm matmul-server",
                        False)
    execute_ssh_command(broker_client, "pkill -f deploy_image.sh")
    execute_ssh_command(server_client, "pkill -f deploy_image.sh")


# Handle CTRL+C (SIGINT)
def signal_handler(sig, frame):
    print("\nCTRL+C detected! Cleaning up before exit...")
    cleanup(server_client, broker_client)
    sys.exit(1)


signal.signal(signal.SIGINT, signal_handler)

# Main loop for executing the tests
for mode in ["Edge", "Server"]:
    for msize in msizes:
        for bsize in bsizes:
            results_dir = os.path.abspath("../results")
            os.makedirs(results_dir, exist_ok=True)
            results_file = os.path.join(results_dir,
                                        f"msize{msize}-bsize{bsize}-mode{mode}.log")

            # Clear log file before running
            open(results_file, 'w').close()

            server_client, broker_client = connect_to_server_through_broker()

            print(f"Deploying edge container for msize={msize}, bsize={bsize}")
            execute_ssh_command(broker_client,
                                "nohup /home/ubuntu/hp2cdt/experiments/response_time_agents/scripts/deploy_image.sh edge > /tmp/edge.log 2>&1 &",
                                False)
            time.sleep(10)
            execute_ssh_command(broker_client,
                                "docker exec matmul-edge compss_agent_add_resources "
                                "--agent_node=192.168.0.118 --agent_port=46101 "
                                "--mem_size=2 192.168.0.118", True)
            time.sleep(4)
            execute_ssh_command(broker_client,
                                "docker exec matmul-edge curl -XGET http://192.168.0.118:46101/COMPSs/resources | jq", True)

            log_thread = threading.Thread(target=monitor_edge_log,
                                          args=(broker_client, results_file))
            log_thread.start()

            if mode == "Server":
                print(
                    f"Deploying server container for msize={msize}, bsize={bsize}")
                execute_ssh_command(server_client,
                                    "nohup ./deploy_image.sh server > /tmp/server.log 2>&1 &",
                                    False)
                time.sleep(10)

                execute_ssh_command(broker_client,
                                    "docker exec matmul-edge compss_agent_add_resources "
                                    "--agent_node=192.168.0.118 --agent_port=46101 "
                                    "--cpu=1 192.168.0.203 Port=46202", False)
                time.sleep(5)

            print(
                f"Executing Matmul operation for msize={msize}, bsize={bsize}")
            for _ in range(5):
                execute_ssh_command(broker_client,
                                    "docker exec matmul-edge compss_agent_call_operation "
                                    "--master_node=192.168.0.118 --master_port=46101 "
                                    f"--cei=\"matmul.arrays.Matmul{mode}Itf\" "
                                    f"matmul.arrays.Matmul {msize} {bsize}",
                                    False)
                time.sleep(5)

            log_thread.join()

            cleanup(server_client, broker_client)

            broker_client.close()
            server_client.close()
