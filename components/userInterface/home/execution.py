"""
This module stores the methods used to manage the executions
(delete, stop, initialise...)
"""

import os
import subprocess
import uuid
from datetime import datetime
from io import StringIO

import paramiko
from django.db.models import Q
from django.shortcuts import render

from home.file_management import remove_directory
from home.forms import ExecutionForm
from home.models import Machine, Execution
from home.ssh import connection_ssh, get_name_fqdn, get_github_code
from home.tool import get_environment_variables


def init_exec(tool_data, request):
    """
    Starts dummy execution (useful for reducing load times). It will give
    initial values for the executions waiting to be queued.

    :param request: request
    :param tool_data: Dictionary containing tool info
    :return: Execution unique identifier (random string)
    """
    machine_found = Machine.objects.get(id=request.session['machine_chosen'])
    user_machine = machine_found.user
    execution = Execution()
    setup = tool_data['setup']
    slurm = tool_data['slurm']
    compss = tool_data['COMPSs']

    execution.machine = machine_found
    execution.author = request.user
    execution.user = user_machine
    execution.status = "INITIALIZING"
    execution.checkpoint = 0
    execution.time = "00:00:00"
    execution.wdir = ""
    execution.setup_path = ""
    execution.execution_time = 0
    execution.jobID = 0
    execution.eID = uuid.uuid4()
    execution.nodes = slurm['Number of Nodes']
    name_sim = setup['Simulation Name']
    execution.name_sim = name_sim
    execution.exec_time = slurm['Time Limit']
    execution.qos = slurm['QOS']
    unique_id = uuid.uuid4()
    execution.name_e = (name_sim + "_" + str(unique_id) + "." + "yaml")
    execution.auto_restart_bool = compss["Autorestart"]
    execution.checkpointBool = compss["Checkpointing"]
    execution.d_bool = compss["Debug"]
    execution.t_bool = compss["Trace"]
    execution.g_bool = compss["Graph"]
    execution.tool = tool_data["tool_name"]
    execution.results_ftp_path = ""
    execution.save()

    return execution.eID


def run_command(command):
    """
    Execute command in the server's machine.

    :param command: command to be executed
    :return: Command output
    """
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=True)
    if result.returncode != 0:
        print(f"Error executing command: {command}")
        print(result.stderr)
        return None
    return result.stdout.strip()


def checkpointing_noAutorestart(jobIDCheckpoint, request):
    ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
    checkpointID = Execution.objects.all().get(author=request.user, jobID=jobIDCheckpoint)
    machine_connected = Machine.objects.get(id=request.session['machine_chosen'])
    command = "source /etc/profile; cd " + checkpointID.wdir + "; source checkpoint_script.sh;"
    stdin, stdout, stderr = ssh.exec_command(command)
    stdout = stdout.readlines()
    s = "Submitted batch job"
    while (len(stdout) == 0):
        import time
        time.sleep(1)
    if (len(stdout) > 1):
        for line in stdout:
            if (s in line):
                jobID = int(line.replace(s, ""))
                request.session['jobID'] = jobID
                form = Execution()
                form.jobID = jobID
                form.eID = uuid.uuid4()
                form.machine_id = checkpointID.machine_id
                form.user = checkpointID.user
                form.author = request.user
                form.nodes = checkpointID.nodes
                form.status = "PENDING"
                form.checkpoint = checkpointID.jobID
                form.time = "00:00:00"
                form.wdir = checkpointID.wdir
                form.workflow_path = checkpointID.workflow_path
                form.execution_time = int(checkpointID.execution_time)
                time = int(checkpointID.execution_time)
                form.name_workflow = checkpointID.name_workflow
                form.qos = checkpointID.qos
                form.name_sim = checkpointID.name_sim
                form.autorestart = checkpointID.autorestart
                form.checkpointBool = checkpointID.checkpointBool
                form.d_bool = checkpointID.d_bool
                form.t_bool = checkpointID.t_bool
                form.g_bool = checkpointID.g_bool
                form.branch = checkpointID.branch
                form.save()
    checkpointID = Execution.objects.all().get(author=request.user, jobID=jobIDCheckpoint)
    checkpointID.status = "CONTINUE"
    checkpointID.save()
    return


def get_id_from_string(machine, author):
    """
    Get machine ID from machine login credentials and author.

    :param machine: Machine login credentials (format user@fqdn)
    :param author: Creator of the machine
    :return: Machine ID
    """
    user, fqdn = get_name_fqdn(machine)
    machine_found = Machine.objects.get(author=author, user=user, fqdn=fqdn)
    return machine_found.id


def stopExecution(eIDstop, request):
    """
    Stop execution (auxiliary view)

    :param eIDstop: Unique identifyer of the execution to be stopped
    :param request: request
    :return: HTML render
    """
    ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
    exec = Execution.objects.filter(eID=eIDstop).get()
    if exec.eID != 0:
        command = "scancel " + str(exec.jobID)
        stdin, stdout, stderr = ssh.exec_command(command)
    Execution.objects.filter(eID=eIDstop).update(status="CANCELLED+")
    form = ExecutionForm()
    executions = Execution.objects.all().filter(author=request.user).filter(
        Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
    executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED")
    executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED")
    executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", autorestart=False,
                                                      checkpointBool=True)
    return render(request, 'pages/executions.html',
                  {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                   'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout})


def delete_parent_folder(path, ssh):
    """
    Remove parent folder (used for deleting an execution and its logs)

    :param path: Path to execution folder
    :param ssh: SSH connection
    :return: None
    """
    parent_folder = os.path.dirname(path)
    command = "rm -rf " + parent_folder + "/"
    stdin, stdout, stderr = ssh.exec_command(command)
    return


def deleteExecution(eIDdelete, request):
    """
    Delete an execution (django model) and its logs

    :param eIDdelete: Unique identifyer of the execution to be deleted
    :param request: request
    :return: HTML render
    """
    try:
        ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
        exec = Execution.objects.filter(eID=eIDdelete).get()
        delete_parent_folder(exec.wdir, ssh)
        if exec.eID != 0:
            command = "scancel " + str(exec.jobID)
            stdin, stdout, stderr = ssh.exec_command(command)
        Execution.objects.filter(eID=eIDdelete).delete()
        log_dir = os.path.join(os.path.dirname(__file__), "..", "logs",
                               f"execution{eIDdelete}")
        remove_directory(log_dir)

        form = ExecutionForm()
        executions = Execution.objects.all().filter(author=request.user).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
        executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED")
        executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED")
        executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", autorestart=False,
                                                          checkpointBool=True)
        return render(request, 'pages/executions.html',
                      {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                       'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout})
    except:
        Execution.objects.filter(eID=eIDdelete).delete()
        form = ExecutionForm()
        executions = Execution.objects.all().filter(author=request.user).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
        executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED")
        executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED")
        executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", autorestart=False,
                                                          checkpointBool=True)
        return render(request, 'pages/executions.html',
                      {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                       'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout})


def update_table(request):
    """
    Update table of execution periodically

    :param request: request
    :return: True
    """
    machine_found = Machine.objects.get(id=request.session['machine_chosen'])
    machineID = machine_found.id
    date_format = "%Y-%m-%dT%H:%M:%S"
    ssh = connection_ssh(request.session["private_key_decrypted"], machineID)
    if not ssh:
        return False
    executions = Execution.objects.all().filter(author=request.user, machine=request.session['machine_chosen']).filter(
        Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
    for executionE in executions:
        if executionE.jobID != 0:
            stdin, stdout, stderr = ssh.exec_command(
                "sacct -j " + str(executionE.jobID) + " --format=jobId,user,nnodes,elapsed,state,submit,start,end | sed -n 3,3p")
            stdout = stdout.readlines()

            values = str(stdout).split()
            Execution.objects.filter(jobID=executionE.jobID).update(time=values[3])

            submit_date = values[5]
            if submit_date != "Unknown":
                submit_date = datetime.strptime(submit_date, date_format)
                Execution.objects.filter(jobID=executionE.jobID).update(
                    submit=submit_date)

            start_date = values[6]
            if start_date != "Unknown":
                start_date = datetime.strptime(start_date, date_format)
                Execution.objects.filter(jobID=executionE.jobID).update(
                    start=start_date)

            end_date = values[7]
            if end_date != "Unknown":
                end_date = datetime.strptime(end_date, date_format)
                Execution.objects.filter(jobID=executionE.jobID).update(
                    end=end_date)

            if str(values[4]) != executionE.status:
                Execution.objects.filter(jobID=executionE.jobID).update(
                    status=values[4], time=values[3],
                    nodes=int(values[2]))

    return True


def execute_command(ssh, cmd):
    """
    Executes a concrete command using a remote ssh connection

    :param ssh: SSH connection
    :param cmd: Involved command
    :return: None
    """
    print()
    print("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&")
    print("EXECUTING COMMAND:")
    print(cmd)
    stdin, stdout, stderr = ssh.exec_command("source /etc/profile; " + cmd)
    print("-------------START STDOUT--------------")
    print("".join(stdout))
    print("---------------END STDOUT--------------")
    print("-------------START STDERR--------------")
    print("".join(stderr))
    print("---------------END STDERR--------------")
    print("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&")
    print()


def run_execution(script, execution_folder, tool_data, entrypoint, setup_path,
                  pythonpath):
    """
    Parse the dictionary (tool_data) in order to retrieve every parameter
    needed for the execution. The method receives a script with other commands,
    and adds the new ones (execution) to it.

    :param script: Script object
    :param execution_folder: Execution folder
    :param tool_data: Dictionary containing tool info
    :param entrypoint: Python module to be executed
    :param setup_path: Path where the setup file (yaml) is stored
    :param pythonpath: Pythonpath
    :return: Script modified
    """
    script.append(f"mkdir -p {execution_folder}")

    slurm_args = ""
    slurm = tool_data["slurm"]

    for arg, value in slurm.items():
        if arg == "Number of Nodes":
            slurm_args += f"--num_nodes={value} "
        if arg == "Project Name":
            slurm_args += f"--project_name={value} "
        if arg == "QOS":
            slurm_args += f"--qos={value} "
        if arg == "Time Limit":
            slurm_args += f"--exec_time={value} "

    compss_args = ""
    compss = tool_data["COMPSs"]

    for arg, value in compss.items():
        if arg == "Debug":
            if value is True:
                compss_args += f"--debug "
        if arg == "Agents":
            if value is True:
                compss_args += f"--agents "
                entrypoint = entrypoint.split("/")[-1]
        if arg == "Graph":
            if value is True:
                compss_args += f"--graph "
        if arg == "Trace":
            if value is True:
                compss_args += f"--tracing "

    job_name = tool_data["setup"]["Simulation Name"] or None
    job_name = job_name.replace(" ", "_")

    if entrypoint.endswith("py"):
        if "agents" in compss_args:
            compss_args += "--lang=PYTHON"
        else:
            compss_args += "--lang=python"

    args = []
    if tool_data['use_args']:
        application_args = tool_data['application']
        for value in application_args.values():
            args.append(value)
    else:
        args.append(setup_path)

    args_str = ' '.join(map(str, args))

    script.append(f"enqueue_compss {slurm_args} {compss_args} --job_name={job_name} "
                  f"--keep_workingdir --log_dir={execution_folder} "
                  f"--job_execution_dir={execution_folder} "
                  f"--pythonpath={pythonpath} {entrypoint} {args_str} "
                  f"--results_dir={execution_folder}/results")
    return script


def export_variables(script, tool_data):
    """
    Append to the script commands for exporting the environment variables.

    :param script: Script object
    :param tool_data: Dictionary containing tool info
    :return: Script modified
    """
    exported_variables = get_environment_variables(tool_data)
    for key, value in exported_variables.items():
        if " " not in key:
            script.append(f"export {key}={value}")
        else:
            print("Error exporting variables: There is a space in the variable "
                  f"name {key}")

    return script


def sftp_upload_repository(local_path, remote_path, private_key_decrypted,
                           machine_id, branch, url, stdout_path, stderr_path,
                           retry=False):
    """
    Upload the codes from a concrete repo from GitHub to the remote machine.

    :param local_path: Local path where the GitHub code is stored
    :param remote_path: Path where to store the GitHub code
    :param private_key_decrypted: Private key decrypted
    :param machine_id: Machine involved's id
    :param branch: Involved branch
    :param url: Repo's URL
    :param retry: Indicates whether the function must be retried or not.
    :return: None
    """
    repo_name = remote_path.split("/")[-1]
    res = get_github_code(repo_name, url, branch, local_path, stdout_path,
                          stderr_path)
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machine_id)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()

    # Check and create remote folder if it doesn't exist
    if not remote_path.startswith('/'):
        stdin, stdout, stderr = ssh.exec_command("echo $HOME")
        stdout = "".join(stdout.readlines()).strip()
        remote_path = stdout + "/" + remote_path

    remote_dirs = remote_path.split('/')
    current_dir = ''
    emptyDir = False
    for dir in remote_dirs:
        if dir:
            current_dir += '/' + dir
            try:
                sftp.stat(current_dir)
            except FileNotFoundError:
                print("Creating directory " + str(current_dir))
                sftp.mkdir(current_dir)
                emptyDir = True

    if res or emptyDir or retry:
        # Recursively upload the local folder and its contents
        for root, dirs, files in os.walk(
                local_path + f"/{repo_name}/" + branch):
            if '.git' in dirs:
                dirs.remove('.git')

            if '.idea' in dirs:
                dirs.remove('.idea')

            # Calculate the relative path from local_path to root
            relative_root = os.path.relpath(root,
                                            local_path + f"/{repo_name}/" + branch)

            # Determine the remote directory
            if relative_root == '.':
                remote_dir = remote_path
            else:
                remote_dir = os.path.join(remote_path, relative_root)

            # Ensure the remote directory exists
            try:
                sftp.stat(remote_dir)
            except FileNotFoundError:
                print(f"{remote_dir} does not exist. Creating it.")
                sftp.mkdir(remote_dir)

            for file in files:
                local_file = os.path.join(root, file)
                remote_file = os.path.join(remote_dir, file)
                try:
                    sftp.put(local_file, remote_file)
                except Exception as e:
                    print(
                        f"Failed to upload {local_file} to {remote_file}: {e}")

    sftp.close()
    return


def install_repos(script, editable, install, install_dir, remote_path,
                  requirements, ssh, target):
    """
    Append to the script the commands for installing (in the remote machine)
    the modules and, if needed, the requirements.

    :param script: Script object
    :param editable: Boolean indicating if the installation must run in
    editable mode
    :param install: Boolean indicating if an installation must be performed
    :param install_dir: Directory (from repo root) where the pip install must be performed
    :param remote_path: Absolute path to the root of the directory
    :param requirements: Boolean indicating if the requirements must be installed
    :param ssh: SSH connection
    :param target: Boolean indicating if the target must be specifyed while
    installing the module
    :return script: Script modified
    :return pythonpath: PYTHONPATH modified
    """
    pythonpath = [remote_path]
    if install:
        if install_dir:
            installation_dir = os.path.join(remote_path,
                                            install_dir)  # install_dir can be empty
        else:
            installation_dir = remote_path

        editable_option = ""
        if editable:
            editable_option = "-e"

        script.append(f"pip install {editable_option} {installation_dir}")
    if requirements:
        target_option = ""
        if target:
            target_option = f"--target={remote_path}/packages"
            pythonpath.append(f"{remote_path}/packages")
        script.append(
            f"pip install -r {remote_path}/requirements.txt {target_option}")
    return script, pythonpath
