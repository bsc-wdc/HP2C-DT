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

msizes = [1, 2, 4, 8]
bsizes = [1, 4, 8, 16, 32, 64, 256]

line_count = 0


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
    global line_count
    while True:
        output = execute_ssh_command(broker_client,
                                     "grep 'Job completed after' /tmp/edge.log",
                                     False)
        lines = [line for line in output.strip().split("\n") if
                 "Job completed" in line]  # Filter valid lines

        with open(results_file, 'w') as f:
            for line in lines:
                f.write(line + '\n')

        if len(lines) != line_count:
            line_count = len(lines)

        print(f"Logged {len(lines)} execution times")

        if len(lines) >= 1:
            print("break")
            break

        time.sleep(5)

    print(f"Final count: {len(lines)}")
    print(f"Storing results in {results_file}")


def cleanup(server_client, broker_client):
    """Stops containers and kills deployment scripts."""
    print("Stopping deployment scripts and containers...")
    execute_ssh_command(broker_client,
                        "docker stop matmul-edge && docker rm matmul-edge",
                        False)
    execute_ssh_command(server_client,
                        "docker stop matmul-server && docker rm matmul-server",
                        True)
    execute_ssh_command(broker_client, "pkill -f deploy_image.sh")
    execute_ssh_command(server_client, "pkill -f deploy_image.sh")


# Handle CTRL+C (SIGINT)
def signal_handler(sig, frame):
    print("\nCTRL+C detected! Cleaning up before exit...")
    cleanup(server_client, broker_client)
    sys.exit(1)


signal.signal(signal.SIGINT, signal_handler)


def main(version):
    global server_client, broker_client, line_count

    print(f"Running experiment with version: {version}")

    # Main loop for executing the tests
    for mode in ["Server"]:
        for msize in msizes:
            for bsize in bsizes:
                line_count = 0
                results_dir = os.path.abspath(f"../results/{version}/raw")
                os.makedirs(results_dir, exist_ok=True)
                results_file = os.path.join(results_dir,
                                            f"msize{msize}-bsize{bsize}-mode{mode}-{version}.log")

                # Clear log file before running
                open(results_file, 'w').close()

                server_client, broker_client = connect_to_server_through_broker()

                print(
                    f"Deploying edge container for msize={msize}, bsize={bsize}")
                execute_ssh_command(broker_client,
                                    f"nohup /home/ubuntu/hp2cdt/experiments/response_time_agents/scripts/deploy_image.sh edge {version} > /tmp/edge.log 2>&1 &",
                                    False)
                time.sleep(10)

                execute_ssh_command(broker_client,
                                    "docker exec matmul-edge curl -XGET http://192.168.0.118:46101/COMPSs/resources | jq",
                                    True)

                if mode == "Server":
                    print(
                        f"Deploying server container for msize={msize}, bsize={bsize}")
                    execute_ssh_command(server_client,
                                        f"nohup /home/ubuntu/hp2cdt/experiments/response_time_agents/scripts/deploy_image.sh server {version} > /tmp/server.log 2>&1 &",
                                        True)
                    time.sleep(10)

                    curl_command = (
                        "docker exec matmul-edge curl -s -XPUT http://192.168.0.118:46101/COMPSs/addResources -H "
                        "\"content-type: application/xml\" -d '<?xml version=\"1.0\" encoding=\"UTF-8\" "
                        "standalone=\"yes\"?><newResource><externalResource><name>192.168.0.203</name>"
                        "<description><processors><processor><name>MainProcessor</name><type>CPU</type>"
                        "<architecture>amd64</architecture><computingUnits>4</computingUnits>"
                        "<internalMemory>-1.0</internalMemory><propName>[unassigned]</propName>"
                        "<propValue>[unassigned]</propValue><speed>-1.0</speed></processor></processors>"
                        "<memorySize>8</memorySize><memoryType>[unassigned]</memoryType><storageSize>-1.0</storageSize>"
                        "<storageType>[unassigned]</storageType><operatingSystemDistribution>[unassigned]</operatingSystemDistribution>"
                        "<operatingSystemType>[unassigned]</operatingSystemType>"
                        "<operatingSystemVersion>[unassigned]</operatingSystemVersion><pricePerUnit>-1.0</pricePerUnit>"
                        "<priceTimeUnit>-1</priceTimeUnit><value>0.0</value><wallClockLimit>-1</wallClockLimit></description>"
                        "<adaptor>es.bsc.compss.agent.comm.CommAgentAdaptor</adaptor>"
                        "<resourceConf xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        "xsi:type=\"ResourcesExternalAdaptorProperties\"><Property><Name>Port</Name>"
                        "<Value>46202</Value></Property></resourceConf></externalResource></newResource>'"
                        )

                    execute_ssh_command(broker_client, curl_command, True)
                    time.sleep(5)

                log_thread = threading.Thread(target=monitor_edge_log,
                                              args=(
                                              broker_client, results_file))
                log_thread.start()

                print(
                    f"Executing Matmul operation for msize={msize}, bsize={bsize}")

                while (line_count < 1):
                    old_counter = line_count
                    execute_ssh_command(broker_client,
                                        "docker exec matmul-edge compss_agent_call_operation "
                                        "--master_node=192.168.0.118 --master_port=46101 "
                                        f"--cei=\"matmul.arrays.Matmul{mode}Itf\" "
                                        f"matmul.arrays.Matmul {msize} {bsize}",
                                        True)

                    while old_counter == line_count:
                        time.sleep(0.5)

                log_thread.join()
                print("------------------------------------------------------")
                cleanup(server_client, broker_client)

                broker_client.close()
                server_client.close()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python run_tests.py <simple|simple_external|matmul>")
        sys.exit(1)

    version = sys.argv[1]

    main(version)