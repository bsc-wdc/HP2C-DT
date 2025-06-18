

# HP2C-DT

Spain has set very ambitious energetic goals to achieve a fully sustainable, decarbonized and resilient energy system, as detailed in the [Plan Nacional Integrado de Energía y Clima (PNIEC) 2021-2030](https://www.miteco.gob.es/images/es/pnieccompleto_tcm30-508410.pdf). By 2030, 74% of power generation should come from renewable energy sources and the country must achieve emission neutrality by 2050, moving towards a 100% renewable energy electricity system.

To achieve such ambitious renewable generation objectives, the [Plan España Digital (PED) 2025](https://www.lamoncloa.gob.es/presidente/actividades/Documents/2020/230720-Espa%C3%B1aDigital_2025.pdf) discusses a key fact, which is the blended evolution of both the energy and digital transition. PED-2025 discusses how digitalization can contribute to achieve a more resilient and clean economy, as a key step to secure the PNIEC defined objectives on reducing greenhouse gases effect by increasing renewable energy generation and energy efficiency increase.

This project embraces the digital and ecological transition to develop a High-Precision High-Performance Computer-enabled Digital Twin (HP2C-DT) for modern power system applications. The HP2C-DT project aims to develop an innovative network Digital Twin (DT) concept, able to represent with a high degree of fidelity modern power systems in a wide variety of applications, such as transmission, distribution, generation, railway and industrial networks. The main project goal is to maximize their resilience and real-time performance during the high-demanding and challenging energy transition, achieving 100% renewable power networks.


![concept](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/concept.png)


## Additional Documentation

- [Start up guide](docs/STARTUP.md)
- [OpenStack Configuration Guide](docs/SERVER_SETUP.md)
- [OpenStack Communications Instructions](docs/SERVER_COMM.md)


## Edge nodes
In our application, we use edge nodes responsible for collecting, processing, and transmitting data between various components of the electric grid. To distribute the computational load of the operations carried out by these edge nodes, we utilize them as COMPSs agents. Within these nodes, you can find various devices, including voltmeters, ammeters, and switches, among others. These devices will enable us to create small smart subgrids. Depending on the nature of the device and its desired utility, these devices can function as sensors, actuators, or both.

### Setup and communications
To configure an edge node, we use JSON files (refer to the `deployments` directory for examples). In this section, we will explain how to create them. An edge setup file will consist of three sections, as described below:

#### Global Properties

```json
"global-properties":{
    "label": "edge1",
    "type": "edge" # json type (edge/server)
    "comms":{
        "opal-tcp": {
            "protocol": "tcp",
            "sensors": {
                "ip": "0.0.0.0",
                "port": "20102"
            },
            "actuators": {
                "ip": ["$LOCAL_IP", "localhost"],
                "port": "30102"
            }
        }
        ...
    },
    "geo-data": {
        "position": {
            "x": 2.1153889,
            "y": 41.389917
        },
        "connections": ["edge2"]
    },
    "window-size": 5, #optional
}
```

This section contains the global properties of the edge node, such as 

- `label`, the name of the edge.
- `comms`, where the user can define communication methods that can be later used by each device separatelly. Each method has a name (the key of the JSON object) and, within the object, a `protocol` (currently we only have `udp` and `tcp`), and how `sensors` and `actuators` will be handled. For each one, specify an IP or IPs (it can also be a list of IPs), and the port. These ports must be unique for every node.
- `geo-data`, which includes the `position` (`x` and `y` coordinates) and `connections` (a list of edge labels).
- `window-size`, which allows us to specify the size of the windows for all devices (this can be overridden for individual devices, as explained below). These windows help reduce communication load by enabling devices to store multiple values locally. Aggregates (described later) can then be performed on the stored data. 


#### Devices

```json
"devices": [
    {
        "label": "Three-Phase Switch Gen1",
        "driver": "es.bsc.hp2c.edge.opalrt.OpalSwitch",
        "driver-dt": "es.bsc.hp2c.server.device.VirtualSwitch",
        "position": {
            "x":0,
            "y":0
        },
        "properties": {
            "comm-type": "opal-tcp",
            "indexes": [0,1,2],
            "window-size": 5,  #optional
            "amqp-trigger": "onRead", #optional
            "amqp-interval": 5 #optional
            "amqp-agg-args": { #optional
                "phasor-freq": 40 #optional
            }   
        }
    },
    ...
]
```

The `devices` section will contain each device within the edge. Each device has the following attributes:
1. `label`: the name of the device, which must be unique within this edge.
2. `driver`: the path to the device's implementation.
3. `driver-dt`: the path to the device's digital twin implementation.
4. `position`: the XY position of the specific device.
5. `properties`: We have two properties:
   - `comm-type`, where the user can refer to the methods declared in the `comms` section in `global-properties`.
   - `indexes`, which serves as a device identifier for those simulated by OpalRT. Indices represent the order in which measurements are sent in a packet from OpalRT. Voltmeters, ammeters, generators, wattmeters, and varmeters can have one or three indexes. Three-phase voltmeters and three-phase ammeters should have three indexes, while switches can have one or three, depending on their number of phases. These indexes must be unique, as they define the correspondent position in the socket received.
   - `window-size`, optional argument where the user can specify the size of the sensor window. It can also be declared in the "global-properties" section and, if neither is provided, it will be 1.
   - `amqp-trigger`, optional argument to specify which type of amqp publish is desired for this concrete device. Options are:
     - **"onRead"**: sends message for each read or set of reads by using `amqp-interval`: [int]
     - **"onFrequency"**: sends messages periodically every n seconds by using `amqp-frequency`: [int]
     - **"onChange"**: Executed whenever the values of the sensors defined in `trigger.parameters` change.
   - `amqp-aggregate`, `amqp-agg-args` optional arguments to specify the type of pre-processing to perform on the sensor window, and its arguments (they can vary depending on the aggregate). Options include:
       - **"sum"**: Sums up the values in the window. Only valid for `Number[]` sensors.
       - **"avg"**: Returns the average of all values in the window. Only valid for `Number[]` sensors.
       - **"last"**: Returns the most recent value in the window.
       - **"all"**: Returns all values in the window, ordered from oldest to newest.
       - **"phasor"**: Returns phasor (magnitude and phase) of the sensor given (only for Ammeter and Voltmeter). Frequency can be specified including the entry "phasor-freq" and a double within the "amqp-agg-args" JSONObject.

These AMQP options can be defined for each sensor by editing the `deployments/defaults/setup/edge_setup.json` file. This file specifies the functions to be performed for each device. Commonly defined functions include:
- **`AMQPConsume`**: A method used by actuators to receive actuations from the server.
- **`AMQPPublish`**: A method used to send sensor readings from the edge to the server. This is where the AMQP parameters for each sensor are defined by default, though they can be overridden for individual devices as shown earlier.
```json
"global-properties":{
    "funcs": [
        {
            "label": "AMQPPublish",
            "lang": "Java",
            "method-name": "es.bsc.hp2c.edge.funcs.AmqpPublish",
            "parameters": {
                "sensors": [],
                "actuators": [],
                "other": {
                    "aggregate": {
                        "type": "phasor" #specify aggregate method
                        "args": { #specify aggregate args (parsed by the aggregate method)
                            "phasor-freq": 60.0 
                        }   
                    }
                } 
            },
            "trigger": {
                "type": "onRead",  # onFrequency also possible
                "parameters": {
                    "trigger-sensor": "",
                    "interval": 3  # if onFrequency, declare "frequency": [int]
                }
        
            }
        },
        {
            "label": "AMQPConsume",
            "lang": "Java",
            "method-name": "es.bsc.hp2c.edge.funcs.AmqpConsume",
            "parameters": {
                "sensors": [],
                "actuators": ["ALL"],
                "other": {}
            },
            "trigger": {
                "type": "onStart",
                "parameters": {}
            }
        }
    ]
}
```


## Functions

To perform any action or evaluation within the system, we can declare **functions**. A function is a Java or Python method (`method-name`) that receives specific parameters (sensors and/or actuators) along with additional user-defined parameters (in the `other` section).

We can also define how or when the function is triggered, allowing the following options:

- **`onRead`**: Executed every time the sensor defined in `parameters` is read.
- **`onChange`**: Executed whenever the values of the sensors defined in `trigger.parameters` change.
- **`onFrequency`**: Triggered every _n_ seconds, where _n_ is specified in `parameters.frequency` (see JSON example in the [`Server`](#server) section).
- **`onStart`**: Runs the function when the DT is initialized.


```json
"funcs": [
    {
        "label": "VoltLimitation",
        "type": "workflow", #optional
        "lang": "Java",
        "method-name": "es.bsc.hp2c.edge.funcs.VoltLimitation",
        "parameters": {
            "sensors": ["Voltmeter Gen1"],
            "actuators": ["Three-Phase Switch Gen1"],
            "other": {
                "threshold": 200
            }
        },
        "trigger": {
            "type": "onRead",
            "parameters": ["Voltmeter Gen1"]
        }

    },
]
```

A function is a method that can read from one or more sensors and actuate over one or more actuators. Within the `funcs` section, the user must specify the following:
1. `label`: a unique name for the function.
2. `lang`: the language in which it was implemented (Java or Python).
3. `method-name`: the path to the method's implementation (e.g., es.bsc.hp2c.<SUBPACKAGE>.<CLASS>).
4. `parameters`: the user must define lists of `sensors`, `actuators`, and additional parameters called `others` that are needed by the function.
5. `trigger`: the event that triggers the execution of the function. 

When a Python function is instantiated (if `lang` is *Python*), a Python server is initialized and communication is 
established via UNIX domain sockets. Therefore, the `method-name` will always be `es.bsc.hp2c.common.utils.PythonFunc`. 
The user may specify it explicitly, but if not provided, this default value will be used. This is because we use a 
unified structure for Python functions that allows us to store the socket and other relevant attributes. For each 
different method, a new UDS (Unix Domain Socket) will be created to communicate with the Python server.

As a result, for these functions, the user must specify within the `other` section the `module_name`, `method_name`, 
and, if applicable, `other_func_parameters`. The following code is an example of a Python function (it is also a 
`VoltLimitation` like the previous example for Java functions):

```json
{
  "label": "VoltLimitation",
  "lang": "Python",
  "parameters": {
    "sensors": ["Voltmeter Gen1"],
    "actuators": ["Three-Phase Switch Gen1"],
    "other": {
      "module_name": "volt_limitation",
      "method_name": "main",
      "other_func_parameters": {
        "threshold": 100
      }
    }
  },
  "trigger": {
    "type": "onRead",
    "parameters": {
      "trigger-sensor": ["Voltmeter Gen1"],
      "interval": 1
    }
  }
}
```

Functions can be executed either as an instantaneous function or as a COMPSs workflow, designed for non-instantaneous, 
larger tasks. In this case, the computational work will be distributed among the available COMPSs agents (explained below). 
To use this COMPSs workflow functionality, the user must specify the `type` field as `workflow`.

### COMPSs Workflows
As every edge device and the server are COMPSs agents, we implemented an optional section, `compss`, allowing the user to
declare both the COMPSs architecture and external resources (other agents to which the declaring agent can offload computational work).
This section applies to both edge and server setups. You can check examples of this section in `deployments/test_response_time/setup/edge1.json`.

This is defined by two subsections:

- `project`: Defines the agent architecture. Every field is optional.
  - `cpu`: Number of computing units (Default: 1)
  - `arch`: Agent architecture (Default: unassigned)
  - `memory`: Available memory of the agent
  - `storage`: Available storage of the agent

- `resources`: List of external agents where computational work can be offloaded.
  - `ip`: Destination IP (Mandatory)
  - `port`: COMM port of the destination agent (Mandatory)
  - `cpu`: Number of CPUs on the remote agent (Default: 1)
  - `arch`: Architecture of the remote agent (Default: unassigned)


```json
"compss": {
    "project":{
      "cpu": "1",
      "arch": "arm",
      "memory": "2",
      "storage": "10"
    },
    "resources": [
      {
        "ip": "212.128.226.53",
        "port": "8002",
        "cpu": "4",
        "arch": "amd64"
      }
    ]
  }
```

## Server
Another key component in our architecture is the Server, which is responsible for receiving data from the edges, storing it in the InfluxDB database, executing functions, and providing data to the User Interface, among other tasks.

To deploy the server, the user must provide a JSON file specifying:

- The "type" as "server" (see example below)
- The "alarm-off-delay" (optional)
- The "funcs" list (as done previously for the edge)

```json
{
  "global-properties": {
    "type": "server",
    "alarm-off-delay": "10000ms" # optional. Specify the time plus the unit (ms, s, m, h)
  },
  "funcs":[
    {
      "label": "VoltageFaultDectection",
      "lang": "Python",
      "method-name": "es.bsc.hp2c.server.funcs.VoltageFaultDetection",
      "parameters": {
        "sensors": {},
        "actuators": [],
        "other": {
          "threshold": 0.2
        }
      },
      "trigger": {
        "type": "onFrequency",
        "parameters": {
          "frequency": 10
        }
      }
    }
  ]
}
```

### Server REST API

In order to get some information from the server and send orders, there is a REST API implemented in the Server. It has several functions like:
- **actuate**: explained in section [`Actuation`](#actuation)
- **getGeoInfo**: returns the geographical information (useful for creating the geomap in UI), and information about whether the edge is available or not ("show"):
  ```bash
  curl -X GET http://0.0.0.0:8080/getGeoInfo
    
  {"edge1":{"show":true,"position":{"x":2.1153889,"y":41.389915},"connections":["edge2"]},
  "edge2":{"show":true,"position":{"x":-5.9826,"y":37.3886},"connections":["edge1"]}}
  ```   
- **getEdgesInfo**: returns information about the whole system (edges, devices and their attributes):
  ```bash
  curl -X GET http://0.0.0.0:8080/getEdgesInfo
     {
        "edge1":{
           "modified":true,
           "is_available":true,
           "info":{
             "VoltmeterGen1":{
               "size":2,
               "isActionable":false,
               "units":"V",
               "type":"Voltmeter",
               "is_available":true,
               "aggregate":"phasor"
             },
             "ThreePhaseSwitchGen1":{
               "isCategorical":true,
               "size":3,
               "isActionable":true,
               "categories":["ON","OFF","NULL"],
               "units":"",
               "type":"Switch",
               "is_available":true,
               "aggregate":"last"
             }
           }
        }
      }
  ```

- **getAlarms**: returns the triples alarm-edge-device declared.
  ```bash
  curl -X GET http://0.0.0.0:8080/getAlarms
    [[VoltageFaultDetection, edge2, VoltmeterGen1], 
    [VoltageFaultDetection, edge1, VoltmeterGen1], 
    [LoadBalanceAlarm, global, global]]
  ```


## Alarms
Alarms are another functionality of our Digital Twin. They are useful for monitoring the system’s state and receiving notifications when unexpected events occur.

To enable this, we implemented a writeAlarm method, which can be called from a function (see the previous section [`Functions`](#functions)) whenever an alarm needs to be triggered.

### Alarms on the Server
The server generates a JSON file in the project root (alarms.json) that summarizes the status of all declared alarms.

In this JSON file, each alarm entry includes a location section listing the edge-device pairs, along with the date and time of the alarm and, if specified, an informational message. If no edge or device is declared, the date and time will be stored directly within the alarm entry rather than in the location subsection.

Here is an example of a JSON file:

```json
{
    "VoltageFaultDetection": { # local alarm
        "alarm": true,
        "location": {
            "edge1": {
                "VoltmeterGen1": {
                    "time": "2025-02-03T14:52:40.687Z",
                    "info": "Fault detected on edge edge1. Voltage is 132.15349%, Threshold is 20.0%"
                }
            },
            "edge2": {
                "VoltmeterGen1": {
                    "time": "2025-02-03T14:52:40.689Z",
                    "info": "Fault detected on edge edge2. Voltage is 60.319984%, Threshold is 20.0%"
                }
            }
        }
    },
    "LoadBalanceAlarm": { # global alarm
        "alarm": true,
        "time": "2025-02-03T14:52:40.686Z",
        "info": "Load imbalance detected: max=620.3006 (edge2-ThreePhaseAmmeterGen1) min=146.368 (edge1-AmmeterGen1)"
    }
}
```
To manage alarm states, we define an alarm off delay (see the [`Server`](#server) section for the JSON definition). This represents the period during which no new true events occur for the alarm, after which it is automatically turned off.

Additionally, alarms are stored in InfluxDB, allowing us to define alert rules in Grafana and monitor their status, as explained in the [`Alarms on the User Interface`](#alarms-on-the-user-interface) section. Each database entry includes:

- Alarm name - *Tag*
- Edge (set to "global" if none) - *Tag*
- Device (set to "global" if none) - *Tag*
- Alarm status (true/false) - *Field*
- Info message - *Field*
- Timestamp

If both the edge and device are set to "global," the alarm is assumed to be related to the entire system rather than a specific device.

### Alarms on the User Interface
Once the alarm statuses are stored in the database, we define alert rules in Grafana to visualize them. We create a new rule for each unique alarm-edge-device combination to ensure all potential issues are monitored.

Additionally, these rules are displayed in a dedicated panel within the User Interface, as shown below:

![alerts-grafana](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/alerts-grafana.png)

## Deployment
Under the `deployments` directory, we provide several deployment bash scripts and two different deployment configuration examples: `testbed`, a simple one with two edges, and `9-buses` (with nine). Each deployment has a setup (previously explained) and a `deployment_setup.json`, which includes the IP and port of every service (refer to the given examples).

The final file involved in the deployment is the `config.json`. It must be manually created by the user and located at the root of the repository:

```
.
├── apps/
├── components/
├── deployments/
├── docker/
├── docs/
└── config.json
```

`config.json` should contain the username and password for the database, as well as the API key for Grafana, as follows:

```json
{
  "database": {
    "username": "XXXX",
    "password": "XXXX"
  },
  "grafana": {
    "api_key": "XXXX"
  }
}
```

The Grafana API key can be either a single key (as shown in the previous example) or multiple keys (specified as a list) if the user wants to configure more than one setup:
```json
{
  "database": {
    "username": "XXXX",
    "password": "XXXX"
  },
  "grafana": {
    "api_key": ["XXXX", "XXXX"]
  }
}
```

In order to run the whole DT architecture, we have the edges, a server, a user interface, a database, and a broker (in charge of queuing and routing the messages).

We made a random data generator for testing the application called "Opal Simulator". Every deployment script mentioned in this section has its own usage with several argument options that can be invoked using the "-h" flag.

To deploy the edges, the user must create its image (`docker/edge/create_images.sh`) and then either execute all of them separately with `docker run` (using the next script as an example) or all together on the same machine:
```bash
./deployments/deploy_edges.sh
```
### Local execution
This option is meant for testing purposes, and allows to create and deploy broker, server, user interface and opal simulator all together by using docker compose:
```bash
./deployments/deploy_all.sh
```
### Distributed execution
1. Create images using the script `docker/create_images.sh`.
2. Run the created images:
```bash
./deployments/deploy_broker.sh
./deployments/deploy_user_interface.sh
./deployments/deploy_server.sh
[./deployments/deploy_opal_simulator.sh]
```


## User Interface
This web application, which can be accessed through {user_interface_ip}:{user_interface_port} in a browser, provides a 
monitor for the deployment. The main view has two panels:
### View Map
Shows the edges' representation and their connections, with a black dot if the edge is running correctly, a yellow dot 
if the edge has some unavailable devices, and a red dot if the entire edge is unavailable. If an edge is clicked, the 
interface will provide a detailed view of the edge.

![ui-view-map](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/UI-View-Map.png)
### View List
Shows every device in every edge as a list. If the user wants to see the real-time state of a device (Grafana panels), 
it can be accessed by clicking on it or on the "View detail" button. If the device is an actuator, there will be another 
button labeled "View detail & actuate". If any component is not available, there will be a red dot next to its name.

![ui-view-list](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/UI-View-List.png)

### HPC executions from UI
The user interface is a web application primarily used for monitoring devices in the grid and controlling some of them,
providing a clear interface between the user and the digital twin. Additionally, the app includes features such as HPC 
executions, which simplify the execution of custom workflows directly from the interface. Here you can find the README:

- [HPC Executions with UI](components/userInterface/README.md)

## IoT communications
The application uses the RabbitMQ Java client for edge devices and a RabbitMQ Docker image for the AMQP broker.

### AMQP broker
The docker container of the broker is based on the RabbitMQ image maintained by the docker community. We add additional 
setups to the configuration files in `/etc/rabbitmq/conf.d/` according to the [RabbitMQ documentation](https://www.rabbitmq.com/configure.html#config-confd-directory).

To deploy this image, either use:
```bash
docker run -it --rm --name hp2c_broker -p 8005:8005 -p 8015:8015 hp2c/broker:latest
```
or use:
```bash
./deployments/deploy_broker.sh
```
Ports 8005 (previously 5672 by default), as the main AMQP port, and 8015 (previously 15672 by default), as the RabbitMQ Management console, are reserved.

To diagnose the RabbitMQ server:
```shell
rabbitmq-diagnostics status
```

#### Additional configuration
- Timestamp is added to each message upon publishing using:
```
message_interceptors.incoming.set_header_timestamp.overwrite = false
```

### Sensors publishing

The basics of the communications are as follows:
- One single exchange called `measurements`
- Edge devices publish messages to a given routing key with the following structure:
  ```
  edge.<EDGE_ID>.sensors.<DEVICE_ID>
  ```
  For instance, if edge1 has two sensors `voltmeter` and `ammeter`, it will publish measurements to `edge.edge1.sensors.voltmeter` and `edge.edge1.ammeter`
- The server's queue is bound to the exchange and any routing key that is a child of `edge` (`edge.#`), e.g., `edge.edge1.sensors.voltmeter`.

### Actuation
We can send commands to actuators through the REST API of the Server at the endpoint `/actuate`

```bash
curl -X POST   -H "Content-Type: application/json"   -d '{"values":["off","off","off"],"edgeLabel":"edge1","actuatorLabel":"ThreePhaseSwitchGen1"}'   http://localhost:8080/actuate

curl -X POST   -H "Content-Type: application/json"   -d '{"values":["0.5","0.75"],"edgeLabel":"edge1","actuatorLabel":"GeneratorGen1"}'   http://localhost:8080/actuate
```

Wrong or malformed commands are gracefully handled and the REST API responds with the right usage.

![actuation](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/actuation.png)

## Database Organization

In the InfluxDB database, `hp2cdt`, each IoT device, labeled as `edge1`, `edge2`, etc., contributes to a *measurement* (a time series in InfluxDB nomenclature) each. To distinguish between devices, a *tag* called `device` is used, and "SensorX" is added to specify each sub-device, even if the sensor has only one sub-sensor. The actual measurements are stored in a float-type *field* named `value`. This setup simplifies the handling and interpretation of data from various IoT sources.

### Database Name: `hp2cdt`

This database contains time series data from IoT devices. Each device is represented by a separate measurement within the database.

### Measurements:

| Measurement Name | Description                            |
|------------------|----------------------------------------|
| `edge1`          | Data from Edge Node 1                  |
| `edge2`          | Data from Edge Node 2                  |
| ...              | ...                                    |

### Tags:

- **`device`**: Represents the name of each device without spaces, dashes, or underscores. Each device name is followed by the string "SensorX" to denote each subsensor.

  Example:
  - `AmmeterGen1Sensor0`
  - `ThreePhaseSwitchGen1Sensor2`

### Fields:

- **`value`**: Floating-point value representing the actual measurements from each device.

### Example:

| Time                 | Device                | Value |
|----------------------|-----------------------|-------|
| 2024-03-05T12:00:00Z | AmmeterGen1Sensor0    | 10.5  |
| 2024-03-05T12:00:00Z | ThreePhaseSwitchGen1Sensor0 | -20.3  |
| 2024-03-05T12:00:00Z | ThreePhaseSwitchGen1Sensor1 | 0.1  |
| 2024-03-05T12:00:00Z | ThreePhaseSwitchGen1Sensor2 | 20.3  |
| 2024-03-05T12:01:00Z | AmmeterGen1Sensor0    | 11.2  |
| 2024-03-05T12:01:00Z | ThreePhaseSwitchGen1Sensor0 | 1.0  |
| 2024-03-05T12:01:00Z | ThreePhaseSwitchGen1Sensor1 | -10.0  |
| 2024-03-05T12:01:00Z | ThreePhaseSwitchGen1Sensor2 | 15.0  |
| ...                  | ...                   | ...   |


## Hypersim
Hypersim is a real-time simulation platform for power system applications. This tool will perform as our "real" devices, generating realistic values for them in a given setup. To connect Hypersim with our edges, we developed an architecture using a bridge application implemented in Python that will act as a broker, connecting Hypersim with the corresponding edge nodes in our architecture.

![hypersim-edge_comms](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/Hypersim-edge_comms.png)

At the bottom of this figure, we show the Hypersim representation of an electric grid. This grid, composed of devices, will be divided into edges based on different criteria like locality, frequent interaction, or computational logic. In this case, the grid shown is divided into seven edges. Each of them will send information to the corresponding edge in the bridge via UDP (for UDP Sensors) or via the Hypersim API (for TCP Sensors). Hypersim will receive actuations also through the API.

The bridge application, responsible for communicating Hypersim with our edge nodes, will have several Edge objects storing the ports and sockets for every interaction. When each of them receives a message coming from Hypersim (UDP sensors), it will redirect it to the UDP-Sensors-port of the corresponding edge and, in the opposite direction (TCP Actuators), it will redirect it to the API. Regarding the TCP Sensors, the Bridge will demand the value from Hypersim (with a certain frequency) and send it to the TCP-Sensors-port of the appropriate edge. The distribution of sensors among protocols typically follows the next convention:

- UDP communications: 
  - high-frequency sensors, such as voltmeters and ammeters
- TCP communications
  - lower-frequency sensors, e.g., power-meters
  - actuators: switches and generators

Regarding port numeration, we use the following pattern (with `{nn}` being the edge number expressed with two digits):

- Edge node (Java) to Bridge (Python).
  - `1{nn}02` for UDP Sensors ports.
  - `2{nn}02` for TCP Sensors.
  - `3{nn}02` for TCP Actuators.
- Bridge (Python) to Hypersim. Since the TCP communication with Hypersim will be performed via API, we only have to decide which port is used for UDP. 
  - `1{nn}04` (The number of the UDP between Edge node and Bridge, `1{nn}02`, plus 2).
