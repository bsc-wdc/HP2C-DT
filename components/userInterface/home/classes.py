"""
This module stores the classes used by the user interface, including
UpdateExecutions (keep the execution history), RunSimulation (in charge of
running executions of custom tools), and Script (stores a list of commands)
"""

import json
import os
import shlex
import threading
import time

import yaml

from home.execution import update_table, run_execution, export_variables, \
    sftp_upload_repository, install_repos
from home.models import Connection, Execution, Machine
from home.ssh import render_right, connection_ssh
from home.utils import wdir_folder
from home.file_management import absolut


class updateExecutions(threading.Thread):
    """
    Class declared in order to keep every execution parameters updated.
    It is called when a connection with a machine is established.
    """
    def __init__(self, request, connectionID):
        threading.Thread.__init__(self)
        self.request = request
        self.timeout = 120 * 60
        self.connectionID = connectionID

    def run(self):
        timeout_start = time.time()
        wrong_tries = 0
        status = False
        while time.time() < timeout_start + self.timeout:
            bool_exception = update_table(self.request)
            if not bool_exception:
                wrong_tries += 1
                if wrong_tries == 3:
                    Connection.objects.filter(user=self.request.user).update(
                        status="Failed")
                    return

            if status != bool_exception:
                status = bool_exception
                if status:
                    st = "Active"
                else:
                    st = "Disconnect"
                Connection.objects.filter(user=self.request.user).update(
                    status=st)
            time.sleep(5)

        Connection.objects.filter(user=self.request.user).update(status="Disconnect", machine=None)
        render_right(self.request)
        return


class RunSimulation(threading.Thread):
    """
    Class created for running executions of custom tools. It will parse the
    form, download the repositories from GitHub, store them in the appropriate
    machine (repos and setup files), install every requirement, and run the execution.
    """
    def __init__(self, tool_data, request, e_id):
        threading.Thread.__init__(self)
        self.e_id = e_id
        self.request = request
        self.tool_data = tool_data

        for key in tool_data.keys():
            if isinstance(tool_data.get(key), str):
                try:
                    self.tool_data[key] = json.loads(tool_data[key])
                except json.JSONDecodeError:
                    pass

        self.application = self.tool_data.get("application", {})
        self.setup = self.tool_data.get("setup", {})
        self.slurm = self.tool_data.get("slurm", {})
        self.compss = self.tool_data.get("COMPSs", {})
        self.environment = self.tool_data.get("environment", {})

    def run(self):
        execution = Execution.objects.get(eID=self.e_id)
        machine_found = Machine.objects.get(
            id=self.request.session['machine_chosen'])

        ssh = connection_ssh(self.request.session["private_key_decrypted"],
                             machine_found.id)
        fqdn = machine_found.fqdn

        principal_folder = self.setup["Working Dir"]
        principal_folder = absolut(principal_folder, ssh)

        wdirPath, nameWdir = wdir_folder(principal_folder)
        setup_file = execution.name_sim.replace(" ", "_") + ".yaml"
        setup_path = f"{principal_folder}/{nameWdir}/setup/{setup_file}"
        install_dir = self.setup["Install Dir"]
        install_dir = absolut(install_dir, ssh)

        tool_data_yaml = yaml.dump(self.tool_data)

        cmd1 = Script(ssh)
        cmd1.append(f"mkdir -p {principal_folder}/{nameWdir}/setup/")
        cmd1.append(f"echo {shlex.quote(tool_data_yaml)} > {setup_path}")
        cmd1.append(f"cd {principal_folder}")
        cmd1.append("BACKUPDIR=$(ls -td ./*/ | head -1)")
        cmd1.append(f"echo EXECUTION_FOLDER:$BACKUPDIR")
        cmd1.execute()

        execution_folder = wdirPath + "/execution"
        setup_folder = wdirPath + "/setup"
        local_path = os.path.join(os.getenv("HOME"), "ui-hp2cdt",
                                  self.tool_data["tool_name"])

        Execution.objects.filter(eID=self.e_id).update(wdir=execution_folder, setup_path=setup_folder)

        github_setup = json.loads(self.setup["github"])
        entrypoint = self.setup["Entry Point"]
        script = Script(ssh)

        # LOAD MODULES
        modules = self.slurm["modules"]
        for module in modules:
            script.append(f"module load {module}")

        dir_name = os.path.dirname(__file__)
        logs_path = os.path.join(dir_name, "..", "logs",
                                 f"execution{self.e_id}")
        os.makedirs(logs_path)
        stdout_path = os.path.join(logs_path, f"execution{self.e_id}.out")
        stderr_path = os.path.join(logs_path, f"execution{self.e_id}.err")

        pythonpath_list = []
        for repo in github_setup:
            repo_name = repo["url"].split("/")[4]
            repo_entrypoint = entrypoint.split("/")[0]
            remote_path = os.path.join(install_dir, repo_name)
            if repo_name == repo_entrypoint:
                entrypoint = os.path.join(install_dir, entrypoint)
            editable = repo["editable"]
            install = repo["install"]
            run_install_dir = repo["install_dir"] # path where to execute pip install
            requirements = repo["requirements"]
            target = repo["target"]
            sftp_upload_repository(local_path=local_path,
                                   remote_path=remote_path,
                                   private_key_decrypted=self.request.session[
                                       "private_key_decrypted"],
                                   machine_id=machine_found.id,
                                   branch=repo["branch"],
                                   url=repo["url"],
                                   stdout_path=stdout_path,
                                   stderr_path=stderr_path)
            print()
            print("UPLOADED REPO", repo["url"])
            print()

            script, pythonpath_repo = install_repos(script, editable, install, run_install_dir,
                                   remote_path, requirements, ssh, target)
            pythonpath_list += pythonpath_repo

        script = export_variables(script, self.tool_data)

        pythonpath_list.append("$PYTHONPATH")
        pythonpath = ":".join(pythonpath_list)

        script = run_execution(script, execution_folder, self.tool_data, entrypoint,
                               setup_path, pythonpath)
        stdout, stderr = script.execute()

        # Save the logs locally
        with open(stdout_path, 'a') as f_out:
            f_out.write("".join(stdout))
        with open(stderr_path, 'a') as f_err:
            f_err.write("".join(stderr))

        s = "Submitted batch job"
        var = ""
        while (len(stdout) == 0):
            time.sleep(1)
        if (len(stdout) > 1):
            for line in stdout:
                if s in line:
                    jobID = int(line.replace(s, ""))
                    Execution.objects.filter(eID=self.e_id).update(jobID=jobID,
                                                                  status="PENDING")
                    self.request.session['jobID'] = jobID
        self.request.session['execution_folder'] = execution_folder
        return


class Script():
    """
    Class created to store a list of commands, which will be executed with
    script.execute()
    """
    def __init__(self, ssh):
        self.script = ""
        self.ssh = ssh

    def __str__(self):
        return self.script

    def append(self, cmd):
        self.script += f"{cmd}; "

    def execute(self):
        """
        Load profile, execute command, and print output
        """
        self.script = "source /etc/profile; " + self.script
        print()
        print("EXECUTING COMMAND:")
        print(self.script.replace(';', ';\n'))
        stdin, stdout, stderr = self.ssh.exec_command(self.script)
        stdout = stdout.readlines()
        stderr = stderr.readlines()
        print("-------------START STDOUT--------------")
        print("".join(stdout))
        print("---------------END STDOUT--------------")
        print("-------------START STDERR--------------")
        print("".join(stderr))
        print("---------------END STDERR--------------")
        return stdout, stderr
