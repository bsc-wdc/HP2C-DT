import os
import re
import paramiko
import time

# Configuration
SSH_KEY = "/home/mauro/claves/hp2cdt-ncloud.pem"
BROKER_IP = "212.128.226.53"
REMOTE_USER = "ubuntu"
JAR_PATH = "/home/ubuntu/matmul.jar"
CONTAINER_NAME = "compss_matmul"
IMAGE_NAME = "compss/compss"
NUM_EXECUTIONS = 15

msizes = [2]
bsizes = [1024]
mode = "Seq"

def connect_ssh():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(BROKER_IP, username=REMOTE_USER, key_filename=SSH_KEY)
    return client


def execute_ssh_command(client, command):
    stdin, stdout, stderr = client.exec_command(command)
    return stdout.read().decode(), stderr.read().decode()


def setup_container(client):
    """Set up fresh container and return when ready"""
    execute_ssh_command(client, f"docker stop {CONTAINER_NAME} || true")
    execute_ssh_command(client, f"docker rm {CONTAINER_NAME} || true")
    execute_ssh_command(client,
                        f"docker run -d --name {CONTAINER_NAME} -v {JAR_PATH}:{JAR_PATH} {IMAGE_NAME} sleep infinity")
    time.sleep(2)  # Wait for container to initialize


def extract_time_ms(output):
    match = re.search(r"Elapsed time: PT(\d+\.?\d*)S", output)
    return int(float(match.group(1)) * 1000) if match else None

def main(version):
    results_dir = os.path.abspath(f"../results/{version}/raw/")
    os.makedirs(results_dir, exist_ok=True)

    broker_client = connect_ssh()

    try:
        for msize in msizes:
            for bsize in bsizes:
                results_file = os.path.join(results_dir,
                                            f"msize{msize}-bsize{bsize}-mode{mode}.log")

                # Clear existing file
                with open(results_file, 'w'):
                    pass

                print(
                    f"\nStarting {NUM_EXECUTIONS} executions for msize={msize}, bsize={bsize}")

                # Set up fresh container for this pair
                setup_container(broker_client)

                for exec_num in range(1, NUM_EXECUTIONS + 1):
                    output, _ = execute_ssh_command(broker_client,
                                                    f"docker exec {CONTAINER_NAME} java -cp {JAR_PATH} matmul.arrays.Matmul {msize} {bsize}")

                    elapsed_ms = extract_time_ms(output)

                    if elapsed_ms is not None:
                        with open(results_file, 'a') as f:
                            f.write(f"Job completed after {elapsed_ms}\n")
                        print(f"  Exec {exec_num}: {elapsed_ms}ms")
                    else:
                        print(f"  Exec {exec_num}: FAILED")

                # Clean up container
                execute_ssh_command(broker_client, f"docker stop {CONTAINER_NAME}")
                execute_ssh_command(broker_client, f"docker rm {CONTAINER_NAME}")

    finally:
        # Final cleanup if any containers remain
        execute_ssh_command(broker_client, f"docker stop {CONTAINER_NAME} || true")
        execute_ssh_command(broker_client, f"docker rm {CONTAINER_NAME} || true")
        broker_client.close()
        print("\nAll tests completed")


if __name__ == "__main__":
    import sys
    if len(sys.argv) != 2:
        print("Usage: python run_tests_sequential.py <simple|simple_external>")
        sys.exit(1)

    version = sys.argv[1]
    main(version)