"""
This module stores the file management functions, mostly used to retrieve the
results from an execution.
"""

import os
from _stat import S_ISDIR
from io import StringIO

import paramiko

from home.models import Machine


def find_file_recursively(sftp, remote_path, file_name):
    """
    Find a file in the subdirectories of a given path

    :param sftp: SFTP connection object
    :param remote_path: Root path
    :param file_name: Name of the file searched
    :return: None
    """
    for entry in sftp.listdir_attr(remote_path):
        entry_path = os.path.join(remote_path, entry.filename)
        if S_ISDIR(entry.st_mode):
            found_path = find_file_recursively(sftp, entry_path, file_name)
            if found_path:
                return found_path
        elif entry.filename == file_name:
            return entry_path
    return None


def absolut(principal_folder, ssh):
    """
    Convert a relative path into an absolute one.

    :param principal_folder: Path involved
    :param ssh: SSH conection (needed for getting the path to HOME)
    """
    if not principal_folder.startswith('/'):
        stdin, stdout, stderr = ssh.exec_command("echo $HOME")
        stdout = "".join(stdout.readlines()).strip()
        principal_folder = stdout + "/" + principal_folder
    return principal_folder


def get_files_r(remote_path, sftp):
    """
    Get pairs of file name - file size, in order to display them in the results view

    :param remote_path: Remote root dir
    :param sftp: SFTP connection
    :return: Pairs of file name - file size within remote_path subdirectories
    """
    files = {}
    for fileattr in sftp.listdir_attr(remote_path):
        if S_ISDIR(fileattr.st_mode):
            files.update(get_files_r(remote_path + "/" + fileattr.filename, sftp))
        else:
            if "pipe" not in fileattr.filename:
                files[fileattr.filename] = fileattr.st_size
    return files


def get_local_files_r(local_path):
    """
    Get pairs of file name - file size, in order to display them in the results view.

    :param local_path: Local root directory.
    :return: Pairs of file path - file size within local_path subdirectories.
    """
    files = {}
    for dirpath, _, filenames in os.walk(local_path):
        for filename in filenames:
            filepath = os.path.join(dirpath, filename)
            files[filepath] = os.path.getsize(filepath)

    return files


def get_files(remote_path, private_key_decrypted, machineID):
    """
    Create SFTP connection and get files within remote_path subdirectories.

    :param remote_path: Remote root dir
    :param private_key_decrypted: Private key decrypted
    :param machineID: ID of the machine involved
    :return files: Pairs of file name - file size within remote_path subdirectories
    """
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machineID)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()

    # Check if the remote path is relative
    if not remote_path.startswith('/'):
        stdin, stdout, stderr = ssh.exec_command("echo $HOME")
        stdout = "".join(stdout.readlines()).strip()
        remote_path = stdout + "/" + remote_path

    files = get_files_r(remote_path, sftp)

    sftp.close()
    ssh.close()

    return files
