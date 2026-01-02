# User Interface - HPC Execution

This document explains how to set up remote machines and execute workflows on them using the UI.

## Connect to HPC Machines
To specify which machines to use for executions, go to the `HPC Machines` option in the left panel. The first time, it will display the following:

![hpc_machines_none](/docs/figures/hpc_machines_none.png)

To define a new machine, click `New machine` (top right), enter the username and FQDN, and click *Define*. The machine will be created. However, you will not be able to connect to it until the SSH key is set up.
To do this, click `SSH Keys` in the left panel. You will see the list of defined machines. Select the machine you want and click *Continue*.

![ssh_keys](/docs/figures/ssh_keys.png)

At the top of the page, there are two buttons: one to copy the token ,which should be securely stored by the user and will be requested by the UI, and another to copy the public key, which should be added to the remote machine, typically in `~/.ssh/authorized_keys` .

**Important:** Do not leave the view without copying both keys, or you will need to generate them again.

Once both keys are copied, click `Continue` to return to the main page. Now, you should be able to connect to the machine. Go to `HPC Machines`, locate the desired machine, paste the stored token, and click *Connect*. If the public key was correctly added to the remote machine's `authorized_keys`, reload the page and the connection should be established.

Note: Some machines may require a VPN connection.

![hpc_machines_connected](/docs/figures/hpc_machines_connected.png)

Once the machine is created, you can delete it (`Delete` button) or modify the username and FQDN via the `Details` button.

## HPC Execution

To execute workflows on a machine, you should connect to it using the `HPC Machines` option, or, if you are already connected, via the `Tools` button. If no connection is established, you will be redirected to the `HPC Machines` view. Otherwise, you will see the executions menu, initially empty with no executions stored and no tools created.

![tools_empty](/docs/figures/tools_empty.png)

To create a new tool, click the `New Tool` button. First, specify the tool name and whether to enable the `Use args` checkbox. This determines whether the application arguments will be passed via a YAML file (by providing its path) or deployed directly after the entrypoint.

You will then be able to configure parameters across five different sections:

- **Application**: Arguments passed to the application, according to the chosen format.
- **Setup**: Specify the GitHub repositories to clone on the remote machine, define whether an installation is required (and its conditions), and set key arguments for the execution setup.
- **Slurm**: Slurm-specific parameters, such as number of nodes, QoS, execution time in minutes, and project name.
- **COMPSs**: COMPSs configuration parameters.
- **Environment**: Define environment variables.

Once a tool is created, it can be downloaded in YAML format and later loaded by another user using the `Upload Tool` button (see the image above). In this project, in `hp2cdt/components/userInterface/templates/tools`, you will find a `Wordcount.yaml` template. This tool can be loaded with `Upload tool` and be tested as a first example of a tool configuration.

When the tool is ready, you can launch it on the HPC machine by clicking the green button at the bottom of the tab. You will then see the following view:

![tools](/docs/figures/tools.png)

In this image, you can see an example of a running execution and a completed one. Executions may also appear in the timeout or failed sections. For each status, you can view the logs by clicking the blue button. This will open a view where you can download each log file. Additionally, you have options to stop and delete an execution (red button), or just stop it (yellow button).

