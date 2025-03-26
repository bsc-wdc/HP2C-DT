import paramiko
import time
import os
import threading

# SSH Configuration
SSH_KEY = "/home/mauro/claves/hp2cdt-ncloud.pem"  # Path to the SSH key
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


def execute_ssh_command(client, command):
    """Executes a command via SSH and returns its output."""
    stdin, stdout, stderr = client.exec_command(command)
    output = stdout.read().decode()
    error = stderr.read().decode()

    print(f"STDOUT: (command {command})\n{output}")
    if error:
        print(f"STDERR: (command {command})\n{error}")

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
    """Monitors the edge log for completion times and saves them to a file."""
    global log_data, stop_logging

    # Clear previous log data
    with log_lock:
        log_data = []

    # Continuously check the edge log for completion times
    while not stop_logging:
        output = execute_ssh_command(broker_client, "cat /tmp/edge.log")

        # Parse the output for completion times
        lines = output.split('\n')
        for line in lines:
            if "App completed after" in line:
                with log_lock:
                    if line not in log_data:  # Avoid duplicates
                        log_data.append(line)
                        print(f"Logged completion time: {line}")

        # Check if we have enough data
        with log_lock:
            if len(log_data) >= 15:
                stop_logging = True
                break
        print (f"Found {len(log_data)} executions")
        time.sleep(5)  # Wait before checking again

    # Save the results to file
    with log_lock:
        with open(results_file, 'w') as f:
            for line in log_data:
                f.write(line + '\n')
        print(f"Saved {len(log_data)} completion times to {results_file}")


# Main loop for executing the tests
for mode in ["Edge", "Server"]:
    for msize in msizes:
        for bsize in bsizes:

            # Create results directory if it doesn't exist
            results_dir = os.path.join(os.path.dirname(__file__), "../results")
            os.makedirs(results_dir, exist_ok=True)
            results_file = os.path.join(results_dir,
                                        f"msize{msize}-bsize{bsize}-mode{mode}.log")

            # Connect to the server via the broker and deploy the server container
            server_client, broker_client = connect_to_server_through_broker()

            print(f"Deploying edge container for msize={msize}, bsize={bsize}")
            execute_ssh_command(broker_client,
                                "nohup ./deploy_image.sh edge > /tmp/edge.log 2>&1 &")
            time.sleep(10)  # Wait for the edge to start

            # Start the log monitoring thread
            log_thread = threading.Thread(
                target=monitor_edge_log,
                args=(broker_client, results_file)
            )
            log_thread.start()

            if mode == "Server":
                print(
                    f"Deploying server container for msize={msize}, bsize={bsize}")
                execute_ssh_command(server_client,
                                    "nohup ./deploy_image.sh server > /tmp/server.log 2>&1 &")
                time.sleep(10)  # Wait for the server to start

                # Add server resources in the broker
                execute_ssh_command(broker_client,
                                    "docker exec -it matmul-edge compss_agent_add_resources "
                                    "--agent_node=192.168.0.118 --agent_port=46101 "
                                    "--cpu=1 192.168.0.203 Port=46202"
                                    )
            time.sleep(5)

            # Run the Matmul operation
            print(
                f"Executing Matmul operation for msize={msize}, bsize={bsize}")
            for _ in range(15):
                execute_ssh_command(broker_client,
                                    "docker exec -it matmul-edge compss_agent_call_operation "
                                    "--master_node=192.168.0.118 --master_port=46101 "
                                    f"--cei=\"matmul.arrays.Matmul{mode}Itf\" "
                                    f"matmul.arrays.Matmul {msize} {bsize}"
                                    )
                time.sleep(10)

            # Signal the logging thread to stop
            stop_logging = True
            log_thread.join()

            # Kill processes after execution to ensure a fresh start for the next combination
            print(f"Stopping containers for msize={msize}, bsize={bsize}")
            execute_ssh_command(broker_client,
                                "docker stop matmul-edge && docker rm matmul-edge")
            execute_ssh_command(server_client,
                                "docker stop matmul-server && docker rm matmul-server")

            # Close broker connection
            broker_client.close()

            # Close server connection
            server_client.close()