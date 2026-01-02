# Start up guide

The following instructions assume a Linux-based distribution. The setup was tested on an Ubuntu 22.04 LTS environment.

## 1. First steps

### 1.1 Download main repository

```bash
git clone <THIS_REPO_URL>
cd hp2cdt
git checkout <REQUIRED_BRANCH>
```

### 1.2 Install dependencies

```bash
sudo apt update
sudo apt install jq -y
```

## 2. InfluxDB

### 2.1 Installation

We preferably use the 1.8.5 version of InfluxDB due to stability and issues in later versions.

```bash
# Get compiled binary from InfluxDB old releases in Github
wget https://dl.influxdata.com/influxdb/releases/influxdb_1.8.5_amd64.deb
sudo dpkg -i influxdb_1.8.5_amd64.deb

# Start influx automatically upon boot
sudo systemctl enable influxdb.service
```

### 2.2 Set up authentication

The influx database used in the project can only be accessed through authentication. To set up this behavior, we use our custom configuration file in `path/to/hp2cdt/components/database/influxdb.conf`:

```bash
# Start service and link custom configuration file to influx
sudo influxd -config components/database/influxdb.conf

# Register user (since auth is on, this is mandatory)
influx -execute "CREATE USER server WITH PASSWORD 'XXXXXXXX' WITH ALL PRIVILEGES"

# Restart Influx to apply the new configuration
sudo systemctl restart influxdb
```

For the password, use the project password.

To test that the user/password pair was created correctly, log into the influx shell:

```bash
influx -username server -password XXXXXXX
```

## 3. Grafana

### 3.1 Installation

```bash
# Pre-requirements
sudo apt-get install -y apt-transport-https software-properties-common wget
sudo mkdir -p /etc/apt/keyrings/
wget -q -O - https://apt.grafana.com/gpg.key | gpg --dearmor | sudo tee /etc/apt/keyrings/grafana.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" | sudo tee -a /etc/apt/sources.list.d/grafana.list

# Update the list of available packages
sudo apt-get update

# Installs the latest OSS release:
sudo apt-get install grafana -y
```

Allow embeddings (not by default). Open the file `/etc/grafana/grafana.ini` with root privileges and modify the following lines:

```
[security]
allow_embedding = true
```

Then, save the changes and start the Grafana service:

```bash
# Start the service
sudo systemctl daemon-reload
sudo systemctl start grafana-server
sudo systemctl status grafana-server

# Verify service is running
sudo systemctl status grafana-server

# Start grafana at boot
sudo systemctl enable grafana-server.service
```

Make sure to restart the service with `sudo systemctl restart grafana-server` if it was already running when doing the `grafana.ini` modifications.

### 3.2 Modify the default Grafana port

Update the configuration file of the service running on port `3000` to use port `8030` instead.

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

Then restart the Grafana service:
```bash
sudo systemctl restart grafana-server
```

### 3.3 Access the Grafana interface

Go to http://localhost:8030/

The default credentials to the Grafana dashboard are `user: admin; password: admin`.

## 4. Docker
### 4.1 Installation

Install Docker for Ubuntu (check https://docs.docker.com/engine/install/ubuntu/ for updated instructions):

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
```

Configure Docker as a non-root user to avoid `sudo` in each command. See https://docs.docker.com/engine/install/linux-postinstall/ for updated instructions or to check common issues:

```bash
sudo groupadd docker
sudo usermod -aG docker $USER
newgrp docker
```

Verify the installation

```bash
docker run hello-world
```

## 5. Set up private credentials

Create the **`config.json`** file in the root of the repository. It is needed in Server and UI for authentication tasks (access to DB and Grafana API key):

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

- Database: the project password
- Grafana API key: custom for each machine. To create one, start the Grafana service and go to  `Settings > Service accounts`

## 6. First deployment

### 6.1 Pull Docker images

Pull DockerHub images (or build local ones).

```bash
docker pull hp2c/broker
docker pull hp2c/server
docker pull hp2c/edge
docker pull hp2c/opal_simulator:1.0
```

### 6.2 Deployment

Use different terminals to run each deployment or run in background.

```bash
cd deployments
./deploy_broker.sh &
./deploy_server.sh &
./deploy_edges.sh &
./deploy_opal_simulator.sh &
```

To run the user interface, load a python environment that complies with the requirements in `path/to/hp2cdt/components/userInterface/requirements.txt` and run:

```bash
cd components/userInterface/scripts
./run_user_interface.sh
```

### 6.3 Check the state of the deployment

Check the output of the different containers:

```bash
docker logs hp2c_edge1
docker logs hp2c_edge2
docker logs hp2c_server
```

Go to http://localhost:8000/ to visualize the user interface.
