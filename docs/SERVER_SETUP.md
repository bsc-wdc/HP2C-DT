
# Remote server and infrastructure setup
Manual modifications of `deployment_setup.json` for every startup in case of dynamic IPs in AWS. Done only once in OpenStack.
```sh
cd hp2cdt
git pull
docker pull hp2c/server
docker pull hp2c/broker
docker pull hp2c/user_interface

# Then make sure config files are updated
vim config.json

# Update IPs
vim deployments/testbed/deployment_setup.json

# (Clustering) Check hostnames in ALL broker locations
vim /etc/hosts

# (Database) start database if not started automatically
sudo influxd

# (Grafana) Check DB ip address used in grafana data source
X.X.X.X:3000/connections/datasources  # in browser
```

## Authentication config file
**`config.json`**, located in the root, is needed in Server and UI for authentication tasks (access to DB and Grafana API key):
```json
{
  "database": {
    "username": "server",
    "password": "XXXXXX"
  },
 "grafana": {
    "api_key": "XXXXXX"
  }
}
```

- DB: our normal password
- Grafana API key: custom for each machine. Check `Settings > Service accounts` in the Grafana user interface.

## Database

### Install InfluxDB
```bash
# Get compiled binary from InfluxDB old releases in Github
wget https://dl.influxdata.com/influxdb/releases/influxdb_1.8.5_amd64.deb
sudo dpkg -i influxdb_1.8.5_amd64.deb

# Start influx automatically upon boot
sudo systemctl enable influxdb.service
```

The following commands are **DEPRECATED** because of needing to downgrade from `v1.8.10` to `v1.8.5` since the newer version failed at restarting at system reboots, i.e. `systemctl enable influxdb` did not work:
```sh
# influxdata-archive_compat.key GPG Fingerprint: 9D539D90D3328DC7D6C8D3B9D8FF8E1F7DF8B07E
wget -q https://repos.influxdata.com/influxdata-archive_compat.key
echo '393e8779c89ac8d958f81f942f9ad7fb82a25e133faddaf92e15b16e6ac9ce4c influxdata-archive_compat.key' | sha256sum -c && cat influxdata-archive_compat.key | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/influxdata-archive_compat.gpg > /dev/null
echo 'deb [signed-by=/etc/apt/trusted.gpg.d/influxdata-archive_compat.gpg] https://repos.influxdata.com/debian stable main' | sudo tee /etc/apt/sources.list.d/influxdata.list

sudo apt-get update && sudo apt-get install influxdb -y
# sudo service influxdb start  # Not needed if later doing `influxd -config`

# Start influx automatically upon boot
sudo systemctl enable influxdb.service
```

**More detail about this issue with `sudo systemctl enable influxdb.service`** not making an effect after reboot:
Issue: https://github.com/influxdata/influxdb/issues/21967

**Possible solutions:**

Trying to modify the systemd file: `/etc/systemd/system/influxd.service` (NOTE: this is a symlink to the actual file in `/lib/systemd/system/influxdb.service`, it is enough to modify the override file in side `/etc/systemd/system/`)  or `./system/multi-user.target.wants/influxdb.service` might be a solution. Parameters suggested are
- Removing `Type=Forking`
- Increasing timeout to 150 s. (`TimeoutStartSec=120` under `[Service]`)

In any case, it is recommended to downgrade to `v1.8.5` or `v1.8.7` instead.

### Set up authentication
**TL;DR**
```bash
# Clone dir
git clone https://gitlab.bsc.es/wdc/projects/hp2cdt.git
cd hp2cdt
git checkout <REQUIRED_BRANCH>

# Start service and link custom configuration file to influx
sudo influxd -config components/database/influxdb.conf

# Register user (since auth is on, this is mandatory)
influx -execute "CREATE USER server WITH PASSWORD 'XXXXXXXX' WITH ALL PRIVILEGES"
```
To test that the user/password pair was created correctly, log into the influx shell:
```bash
influx -username server -password XXXXXXX
```

**Detailed description**
To change the default username and password in InfluxDB, you can follow these steps:
1. **Access the Configuration File**: Locate the configuration file for InfluxDB. The location of this file can vary depending on your installation method and operating system. Common locations include `/etc/influxdb/influxdb.conf` for Linux installations.
2. **Edit the Configuration File**: Open the configuration file using a text editor. Look for the `[http]` section in the file. Within this section, you should find parameters related to authentication, such as `auth-enabled`.
3. **Enable Authentication**: If authentication is not already enabled (`auth-enabled = false`), set it to `true`. This step ensures that users need to authenticate to access the database.
4. **Create New User**: You can create a new user using the InfluxDB command-line interface (CLI) or the InfluxDB web interface (if available). Here's an example of creating a new user named `myuser` with a password `mypassword` using the CLI:
   ```
   influx
   > CREATE USER myuser WITH PASSWORD 'mypassword' WITH ALL PRIVILEGES
   ```
5. **Restart InfluxDB**: After making changes to the configuration file, save the changes and restart the InfluxDB service to apply the new configuration:
   ```
   sudo systemctl restart influxdb
   ```

## Grafana server
### Install
```bash
# Install Grafana
sudo apt-get install -y apt-transport-https software-properties-common wget
sudo mkdir -p /etc/apt/keyrings/
wget -q -O - https://apt.grafana.com/gpg.key | gpg --dearmor | sudo tee /etc/apt/keyrings/grafana.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" | sudo tee -a /etc/apt/sources.list.d/grafana.list
# Updates the list of available packages
sudo apt-get update
# Installs the latest OSS release:
sudo apt-get install grafana -y

# Set up embbeding (not by default)
sudo vim /etc/grafana/grafana.ini
# Modify the following lines
	[security]
	allow_embedding = true
# Save the changes and then restart the Grafana service
#  sudo systemctl restart grafana-server

# Start the service
sudo systemctl daemon-reload
sudo systemctl start grafana-server
sudo systemctl status grafana-server

# Verify service is running
sudo systemctl status grafana-server

# Start grafana at boot
sudo systemctl enable grafana-server.service
```

### Allow embedded panels
Modify file `/etc/grafana/grafana.ini` to allow embedding:
```
# File grafana.ini
allow_embedding = true 
```

And restart the grafana service if needed:
```bash
sudo systemctl restart grafana-server
```

### Configure data source (not needed anymore)
Should not be needed anymore, is handled automatically by the user interface.

`Connections > Add new connection` : Search InfluxDB

### User/password
- User: admin
- Password: generic password

## EC2 setup for docker-based images

Follow the instructions below to set up AWS EC2 virtual machines for HP2C-DT use.

```bash
# Add Docker's official GPG key:
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

# Install
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

# Create docker group to avoid `sudo`
sudo groupadd docker
sudo usermod -aG docker $USER
newgrp docker


# Other dependencies
sudo apt install jq -y

# Download HP2C-DT repo
git clone https://gitlab.bsc.es/wdc/projects/hp2cdt.git
cd hp2cdt
git checkout <REQUIRED_BRANCH>

# CREATE CONFIG FILE config.json AT THE ROOT MANUALLY
echo '{
  "database": {
    "username": "server",
    "password": "XXXX"
  },
 "grafana": {
    "api_key": "XXXXXX"
  }
}' > config.json

# Pull images
docker pull hp2c/broker
docker pull hp2c/server
```
## Brokers (clustering)
Requirements
- The containers in AWS cannot use `--network host` (command gets stuck). Therefore we need to publish all ports one by one when running `docker run`.
- We need to write the `etc/hosts` file for all broker localhosts (not needed to modify the internal container `hosts` file of containers). The hostname added, associated with the corresponding IP, needs to be the same as de RabbitMQ file, currently in `20-main.conf` such as
```
cluster_formation.classic_config.nodes.1 = rabbit@hp2c_broker_server
```

## Security group
We need to modify the security group associated with instances to admit connections through specific ports (by default, only ports 443, 22 and 80 are allowed):
- `Port 5672/8005`: main port for RabbitMQ broker connections.
- `Port 15672/8015`: RabbitMQ management interface (allows connecting to the broker monitor)
- `Port 8080`: Server REST API
- `Port 3000/8030`: Grafana interface
- `Port 4369/8069`: epmd RabbitMQ discovery daemon
- `Port 25672/8025`: RabbitMQ node-to-node communication