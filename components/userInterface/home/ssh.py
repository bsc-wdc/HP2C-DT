"""
This module manages the ssh connections to the external machines.
"""

import os
import subprocess
from io import StringIO

import paramiko
from cryptography.fernet import Fernet
from django.shortcuts import render

from home.forms import Machine_Form

from home.models import Connection, Machine


def remove_numbers(input_str):
    """
    Split the input string by '.' to separate the hostname and domain

    :param input_str: hostname and domain of the machine (ex: glogin4)
    :return: Gets the domain number of the machine
    """

    parts = input_str.split('.')

    if len(parts) >= 2:
        # Take the first part as the hostname
        hostname = parts[0]
        if input_str.startswith("glogin"):
            return "mn5"
        # Remove any trailing digits from the hostname
        while hostname and hostname[-1].isdigit():
            hostname = hostname[:-1]

        return hostname
    else:
        # If there are not enough parts, return the original string
        return input_str


def encrypt(message: bytes, key: bytes) -> bytes:
    """
    Method used for encrypting the private key (in order to store it in the db)
    using a token.

    :param message: Message involved
    :param key: token involved
    :return: private key encrypted
    """
    return Fernet(key).encrypt(message)


def decrypt(token: bytes, key: bytes) -> bytes:
    """
        Method used for decrypting the private key (in order to store it in the db)
        using a token.

        :param message: Message involved
        :param key: token involved
        :return: private key decrypted
        """
    try:
        res = Fernet(key).decrypt(token)
    except Exception as e:
        print("Error decrypting token: %s", str(e))
        raise
    return res


def get_connection_status(request):
    connection = Connection.objects.filter(user=request.user)
    if len(connection) == 0:
        return "Disconnect"
    else:
        return connection[0].status


def get_name_fqdn(machine):
    """
    Receive machine login credentials and return (separately) the user and fqdn

    :param machine: Machine login credentials (format: user@fqdn)
    :return user: User
    :return fqdn: FQDN
    """
    user = machine.split("@")[0]
    fqdn = machine.split("@")[1]
    return user, fqdn


def check_connection(request):
    """
    Checks if a connection is active for a concrete user.

    :param request: Request
    :return: Boolean
    """
    connection = Connection.objects.filter(user=request.user, status="Active")
    if len(connection) > 0:
        return True
    return False


def connection_ssh(private_key_decrypted, machineID):
    """
    Tries to establish a connection to a machine using its private key.

    :param private_key_decrypted: Machine's private key decrypted
    :param machineID: ID of the machine involved
    :return: SSH object/error/redirect to tools view
    """
    try:
        ssh = paramiko.SSHClient()
        pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
        machine_found = Machine.objects.get(id=machineID)
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
        return ssh
    except paramiko.AuthenticationException as auth_error:
        print(f"Authentication error: {auth_error}")
        return None
    except paramiko.BadHostKeyException as host_key_error:
        print(f"Bad host key error: {host_key_error}")
        return None
    except paramiko.SSHException as ssh_error:
        print(f"SSH error: {ssh_error}")
        return None
    except Machine.DoesNotExist as not_found_error:
        print(f"Machine not found error: {not_found_error}")
        return None
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return None


def render_right(request):
    form = Machine_Form()
    checkConnBool = check_connection(request)
    machines_done = populate_executions_machines(request)
    if not checkConnBool:
        if not machines_done:
            request.session['firstCheck'] = "no"
        request.session["checkConn"] = "Required"
    return render(request, 'pages/hpc_machines.html',
                  {'form': form, 'machines': machines_done,
                   'firstPhase': request.session['firstPhase'],
                   'connected': checkConnBool,
                   'check_existence_machines':
                       request.session['check_existence_machines']})


def https_to_ssh(git_url):
    """
    Convert https url to ssh url.

    :param git_url: Git URL in http format
    :return: Git URL in ssh format
    """
    if git_url.startswith("https://"):
        git_url = git_url.replace("https://", "")
        parts = git_url.split('/')
        if len(parts) < 2:
            raise ValueError("Invalid Git URL")

        domain = parts[0]
        user_repo = "/".join(parts[1:])
        return f"git@{domain}:{user_repo}.git"

    raise ValueError("URL is not in HTTPS format")


def get_github_code(repository, url, branch, local_folder, stdout_path,
                    stderr_path):
    """
    Executes a bash script to get the code from GitHub (clone repo locally)
    """
    ssh_url = https_to_ssh(url)
    local_path = os.path.dirname(__file__)
    script_path = f"{local_path}/../scripts/git_clone.sh"
    print("")
    print(f"Getting code from github repo {repository}...")
    try:
        with open(stdout_path, 'a') as stdout_file, open(stderr_path,
                                                          'a') as stderr_file:
            stdout_file.write("\n")
            stdout_file.write(
                f"Getting code from GitHub repo {repository}...\n")
            stdout_file.write("\n")

            stderr_file.write("\n")
            stderr_file.write(
                f"Getting code from GitHub repo {repository}...\n")
            stderr_file.write("\n")

            result = subprocess.run(
                [script_path, repository, ssh_url, branch, local_folder],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )

            stdout_file.write(result.stdout)
            stdout_file.write("\n")

            stderr_file.write(result.stderr)
            stderr_file.write("\n")

            print("-------------START STDOUT--------------")
            print(result.stdout)
            print("-------------END STDOUT--------------")

            # Check the output
            if "Repository not found. Cloning repository..." in result.stdout:
                return True
            elif "Changes detected. Pulling latest changes..." in result.stdout:
                return True
            else:
                if result.stderr:
                    print("-------------START STDERR--------------")
                    print(result.stderr)
                    print("-------------START STDERR--------------")
    except subprocess.CalledProcessError as e:
        print(f"Script execution failed with error code {e.returncode}: {e.stderr.decode('utf-8')}")
    except FileNotFoundError:
        print(f"Error: The script '{script_path}' was not found.")
    return False


def populate_executions_machines(request):
    """
    Get the machines created by the user

    :param request: request
    :return: created machines
    """
    machine_connected = get_machine_connected(request)
    machines = Machine.objects.all().filter(author=request.user)
    machines_done = []
    if machine_connected is not None:
        machines_done.append("" + str(machine_connected.user) + "@" + machine_connected.fqdn)
    if machines.count() != 0:
        for machine in machines:
            if machine != machine_connected:
                machines_done.append("" + str(machine.user) + "@" + machine.fqdn)
    return machines_done


def get_machine_connected(request):
    """
    Get machine connected

    :param request: request
    :return: machine_connected (django model)
    """
    connection = Connection.objects.filter(user=request.user, status="Active")
    machine_connected = None
    if len(connection) > 0:
        connection = connection[0]
        machine_connected = connection.machine
    return machine_connected
