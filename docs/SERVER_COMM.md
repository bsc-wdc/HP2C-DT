# OpenStack setup
- Create Key Pair: download `.pem` file with credentials. It can later be used to ssh into the VM that has attached the floating public IP:
```bash
sudo ssh -i ~/keys/hp2cdt-ncloud.pem ubuntu@FLOATING_IP
```
- Create virtual machine, assign public IP, log in through ssh, and change password. This allows logging in through the dashborad instead of through ssh, since we can only ssh the machine with the 
- Port tunneling

# Port forwarding
With either
- `iptables`
- `socat` or `sshuttle`

#### Port forwarding with socat
From the VM with the public IP (broker):
```bash 
sudo apt-get install socat
socat TCP-LISTEN:8000,fork,reuseaddr TCP:192.168.0.154:8000
socat TCP-LISTEN:8030,fork,reuseaddr TCP:192.168.0.154:8030
```
Here we forwarded the ports 8000 (UI) and 8030 (Grafana) from the UI virtual machine to the broker machine (use `fork` and `reuseaddr` to allow multiple connections and fast ip:port reuse, but [be aware of the caveats](https://stackoverflow.com/a/3233022/1331399)).

#### Test the communication availability
Using `nc` or `telnet`, one can easily try a communication to a specific port.

- Start a listener in the VM with the open port:
```bash
nc -l -p 8000
```
(`telnet -l 8000` hasn't worked for me)

- Communicate with this port from other machine:
```bash
nc YOUR_MACHINE_IP 8000
```
or 
```bash
telnet YOUR_MACHINE_IP 8000
```

#### To automate the process on startup and handle socat failure
The socat command needs to be running on background, so it will reset if the broker machine shuts down. A service can be configured upon startup.

1. Create a script with the socat commands and check that the service will execute

```bash
sudo nano /usr/local/bin/socat-forwarding.sh
```

2. Add the required port forwarding steps:

Paste the following commands,

```bash
#!/bin/bash

# Ensure socat is forwarding on port 8000
if ! pgrep -f "socat TCP-LISTEN:8000"; then
  /usr/bin/socat TCP-LISTEN:8000,fork,reuseaddr TCP:192.168.0.154:8000 &
fi

# Ensure socat is forwarding on port 8030
if ! pgrep -f "socat TCP-LISTEN:8030"; then
  /usr/bin/socat TCP-LISTEN:8030,fork,reuseaddr TCP:192.168.0.154:8030 &
fi

# Wait to keep the script running
wait
```

3. Create a systemd Service File

Create a new systemd service file for socat. Open a terminal and run:
```bash
sudo nano /etc/systemd/system/socat-forwarding.service
```
4. Add Service Configuration

Paste the following configuration into the file:
```makefile
[Unit]
Description=Socat Port Forwarding Service for Multiple Ports
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/socat-forwarding.sh
Restart=always
RestartSec=10s
StartLimitIntervalSec=500
StandardOutput=syslog
StandardError=syslog
TimeoutStartSec=60
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
```

5. Save and Close the File

Press Ctrl + X, then Y, and Enter to save and exit nano.

6. Reload systemd and Enable the Service

Run the following commands to reload systemd and enable the newly created service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable socat-forwarding.service
```

7. Start the Service

Finally, start the socat service:
```bash
sudo systemctl start socat-forwarding.service
```

Now, socat will automatically start at boot time and handle the port forwarding from port 8000 on the "broker" machine to port 8000 and 8030 on the "ui" machine. If the socat process stops for any reason, systemd will automatically restart it, ensuring continuous port forwarding functionality.

# Port Mapping

- **Port 5672 (AMQP)**: Common RabbitMQ port for messaging protocol.
- **Port 15672 (Management UI)**: Web-based UI for managing RabbitMQ.
- **Port 3000 (Development tools like Grafana)**: Unique mapping needed.
- **Port 4369 (Erlang Port Mapper Daemon)**: Needs a specific port in the range.
- **Port 25672 (RabbitMQ distribution)**: Needs an alternative in the range.

For these ports, here's the suggested mapping:

- **5672 → 8005** (AMQP)
- **15672 → 8015** (Management UI)
- **4369 → 8069** (Erlang Port Mapper Daemon)
- **25672 → 8025** (RabbitMQ distribution)
- **3000 → 8030** (Grafana development tools)

The rest stay as normal:
- **8000** (User interface debug)
- **8080** (REST API)
### Configuration Steps

#### RabbitMQ Configuration

1. AMQP Port (5672)

Edit rabbitmq.conf:
```
listeners.tcp.default = 8005
```

2. Management UI Port (15672)

Edit rabbitmq.conf:
```
management.tcp.port = 8015
```

3. Erlang Port Mapper Daemon (4369)

Set the environment variable in the docker container:
```
ERL_EPMD_PORT=8069
```

4. Distribution Port (25672)

Set the environment variable in the docker container:
```
RABBITMQ_DIST_PORT=8025
```

5. Development Tools Grafana (3000)

3000 → 8030

Update the configuration file of the service running on port 3000 to use port 8030 instead.

#### Grafana configuration
In `/etc/grafana/grafana.ini`, uncomment and change port:
```
# The http port  to use
;http_port = 3000
```
by 
```
# The http port  to use
http_port = 8030
```
Then restart Grafana service

#### Restart Services

After updating the configurations, restart all services to apply the new port settings.
Final Configuration Example for rabbitmq.conf
```
listeners.tcp.default = 8005
management.listener.port = 8015
```

Final Configuration Example for the docker container
```bash 
# deploy_broker.sh
ERL_EPMD_PORT=8069
RABBITMQ_DIST_PORT=8025

docker run \
    ... \
    -e ERL_EPMD_PORT=${ERL_EPMD_PORT} \
    -e RABBITMQ_DIST_PORT=${RABBITMQ_DIST_PORT} \
```


# Resetting the ubuntu password
If you need to change the password for the `ubuntu` user on the OpenStack Ubuntu instances via SSH but don't know the current password, you can still reset it using root privileges:

1. **Log in as Root:** First, log in to the instance via SSH using the private key associated with your SSH key pair. Once logged in, you can switch to the root user using the `sudo` command:

   ```bash
   ssh -i /path/to/your/key.pem ubuntu@instance_public_ip
   sudo su -
   ```

   Replace `/path/to/your/key.pem` with the actual path to your PEM key file.

2. **Reset the Password:** With root privileges, you can reset the password for the `ubuntu` user without needing the current password. Use the `passwd` command followed by the username (`ubuntu`) to set a new password:

   ```bash
   passwd ubuntu
   ```

   You'll be prompted to enter a new password for the `ubuntu` user. After typing the new password and confirming it, the password for the `ubuntu` user will be updated.

3. **Logout:** After changing the password, you can exit the root shell and the SSH session:

   ```bash
   exit
   exit
   ```

   This will return you to your local shell.
