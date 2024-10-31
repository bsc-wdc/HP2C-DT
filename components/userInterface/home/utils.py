"""
This module contains various auxiliary functions for diverse purposes.
"""

import os
import random
import string
import uuid


def wdir_folder(principal_folder):
    """
    Obtain an unique ID folder and create an unique execution name

    :param principal_folder: Original folder
    :return wdirDone: path to execution folder
    :return nameWdir: execution directory name
    """
    uniqueIDfolder = uuid.uuid4()
    nameWdir = "execution_" + str(uniqueIDfolder)
    if not principal_folder.endswith("/"):
        principal_folder = principal_folder + "/"
    wdirDone = principal_folder + "" + nameWdir
    return wdirDone, nameWdir


def get_file_extension(file_path):
    """
    Get file extensions

    :param file_path: Path to the file
    :return: extension
    """
    _, extension = os.path.splitext(file_path)
    return extension


def get_random_string(length):
    """
    Generate a random string of specified length combining lower and upper case
    letters.

    :param length: Length of the message
    :return: random string
    """
    result_str = ''.join(random.choice(string.ascii_letters) for i in range(length))
    return result_str
