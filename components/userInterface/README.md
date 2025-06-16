# User Interface - HPC Execution

This document explains how to set up remote machines (e.g., nord4, mn5) and execute workflows on them using the UI.

## Connect to HPC Machines
To specify which machines to use for executions, go to the `HPC Machines` option in the left panel. The first time, it will display the following:

![hpc_machines_none](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/hpc_machines_none.png)

To define a new machine, click `New machine` (top right), enter the username and FQDN, and click *Define*. The machine will be created. However, you will not be able to connect to it until the SSH key is set up.
To do this, click `SSH Keys` in the left panel. You will see the list of defined machines. Select the machine you want and click *Continue*.

![ssh_keys](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/ssh_keys.png)

At the top of the page, there are two buttons: one to copy the token ,which should be securely stored by the user and will be requested by the UI, and another to copy the public key, which should be added to the remote machine, typically in `~/.ssh/authorized_keys` .
**Important:** Do not leave the view without copying both keys, or you will need to generate them again.
Once both keys are copied, click `Continue` to return to the main page. Now, you should be able to connect to the machine. Go to `HPC Machines`, locate the desired machine, paste the stored token, and click *Connect*. If the public key was correctly added to the remote machine's `authorized_keys`, reload the page and the connection should be established.
Note: Some machines may require a VPN connection.

![hpc_machines_connected](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/hpc_machines_connected.png)

Once the machine is created, you can delete it (`Delete` button) or modify the username and FQDN via the `Details` button.

## HPC Execution
To execute workflows in a machine, you should connect to the machine using the `HPC Machines` option or, if you are 
already connected, through the `Tools` button. If there is no connection established, you will be redirected to the 
`HPC Machines` view. Otherwise, you will see the executions menu, with no executions stored and no tools created.

![tools_empty](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/tools_empty.png)

In order to create new tools, you should click the `New tool` button. First you will have to specify the tool name and
the `Use args` checkbox, choosing whether the application arguments will be passed in a yaml file (passing the path to the 
yaml), or deployed after the entrypoint. Then you will be able to configure attributes from 5 different fields:
 - Application: these arguments will be passed to the application in the formats previously explained.
 - Setup: include the GitHub repos needed (they will be cloned in the remote machine), and whether an installation is required (and its conditions), and then some key arguments for the execution setup.
 - Slurm: slurm arguments (number of nodes, qos, execution time in minutes, and project name).
 - COMPSs: COMPSs arguments.
 - Environment: define environment variables.

Once a Tool is created it is possible to download it in a yaml format, so that it can be loaded by other user by clicking `Upload Tool` (see the previous picture).

When the Tool is ready to be executed, you can launch it in the HPC machine by clicking the green button at the bottom of
the tab. Then you will see this view:

![tools](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/tools.png)

In this picture you can see an example of a running execution and a completed one. An execution can also be in the 
timeout and failed sections, and for every different status you can check the logs by clicking the blue button. This 
will display a view where the user could download each file. There is also the option to stop and delete (red button), 
or only stop (yellow button) an execution.  
