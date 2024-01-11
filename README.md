

# HP2C-DT

Spain has set very ambitious energetic goals to achieve a fully sustainable, decarbonized and resilient energy system, as detailed in the [Plan Nacional Integrado de Energía y Clima (PNIEC) 2021-2030](https://www.miteco.gob.es/images/es/pnieccompleto_tcm30-508410.pdf). By 2030, 74% of power generation should come from renewable energy sources and the country must achieve emission neutrality by 2050, moving towards a 100% renewable energy electricity system. 

To achieve such ambitious renewable generation objectives, the [Plan España Digital (PED) 2025](https://www.lamoncloa.gob.es/presidente/actividades/Documents/2020/230720-Espa%C3%B1aDigital_2025.pdf) discusses a key fact, which is the blended evolution of both the energy and digital transition. PED-2025 discusses how digitalization can contribute to achieve a more resilient and clean economy, as a key step to secure the PNIEC defined objectives on reducing greenhouse gases effect by increasing renewable energy generation and energy efficiency increase.

This project embraces the digital and ecological transition to develop a High-Precision High-Performance Computer-enabled Digital Twin (HP2C-DT) for modern power system applications. The HP2C-DT project aims to develop an innovative network Digital Twin (DT) concept, able to represent with a high degree of fidelity modern power systems in a wide variety of applications, such as transmission, distribution, generation, railway and industrial networks. The main project goal is to maximize their resilience and real-time performance during the high-demanding and challenging energy transition, achieving 100% renewable power networks.


  ![concept](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/concept.png)



## Edge nodes
In our application, we use edge nodes responsible for collecting, processing, and transmitting data between various components of the electric grid. To distribute the computational load of the operations carried out by these edge nodes, we utilize them as COMPSs agents. Within these nodes, you can find various devices, including voltmeters, ammeters, and switches, among others. These devices will enable us to create small smart subgrids. Depending on the nature of the device and its desired utility, these devices can function as sensors, actuators, or both.

### Setup and communications
To configure an edge node, we use JSON files (refer to the "deployments" directory for examples). In this section, we will explain how to create them. An edge setup file will consist of three sections, as described below:

#### Global Properties
This section contains global properties of the edge node, such as its "label" and its connection ports ("comms"). In "comms," the user should provide three objects:
1. "udp": these ports and IP will be used for receiving UDP sensors data from the network.
2. "tcp-sensors": these ports and IP will be used for receiving TCP sensors data from the network.
3. "tcp-actuators": these ports will be used for sending data to the network (the edge will be writing through this port). The deployment script will automatically retrieve the local IP, so there is no need to specify an IP manually.

Within these objects, the user must declare a pair port-list of devices. At the moment, the list of devices will remain unused, and we will only check the key provided. These ports must be unique for every node.

#### Devices
The "devices" section will contain each device within the edge. Each device has the following attributes:
1. "label": the name of the device, which must be unique within this edge.
2. "driver": the path to the device's implementation.
3. "position": the XY position.
4. "properties": We have only one property called "indexes" which serves as a device identifier for those simulated by OpalRT. Indices represent the order in which measurements are sent in a packet from OpalRT. Voltmeters, ammeters, generators, wattmeters, and varmeters should have only one index. Three-phase voltmeters and three-phase ammeters should have three indexes, while switches can have one or three, depending on their number of phases. These indexes must be unique, as they define the correspondent position in the socket received.

#### Functions
A function is a method that can read from one or more sensors and actuate over one or more actuators. Within the "functions" section, the user must specify the following:
1. "label": a unique name for the function.
2. "lang": the language in which it was implemented (Java or Python).
3. "method-name": the path to the method's implementation (e.g., es.bsc.hp2c.<SUBPACKAGE>.<CLASS>).
4. "parameters": the user must define lists of "sensors", "actuators", and additional parameters called "others" that are needed by the function.
5. "trigger": the event that triggers the execution of the function. As for "type", it can be "onFrequency" (executed with a periodicity specified in "parameters") and "onRead" (executed after every read of the devices declared as sensors). Additionally, the user should provide a "method-name" (path to the implementation of those triggers).

### Deployment
To run the application, we provide a `testbed` example using Docker containers named 'hp2c_' plus the label specified in 'global-properties'. In this case, each edge node is a different COMPSs agent with ports starting in 4610 and ending with 1 (REST port) and 2 (COMM port). We use the host network for communications with these containers. 

In order to test the application, it must be performed:
1. Create images using the script `docker/create_images.sh`.
2. Run the created image. An example of a deployment script can be found in `deployments/testbed`.
3. Send data. Recall that the declared indexes for each device represent the positions where the values of each device will be searched, so the socket sent must have at least size max(index) - min(index) + 1. There is an example of a random data server in `components/opalSimulator/scripts`.


## IoT communications
The application uses the RabbitMQ Java client for edge devices and a RabbitMQ Docker image for the AMQP broker.

The basics of the communications are as follows:
- One single exchange called `measurements`
- Edge devices publish messages to a given routing key with the following structure: `edge.<EDGE_ID>.<DEVICE_ID>`. For instance, if edge1 has two sensors `voltmeter` and `ammeter`, it will publish measurements to `edge.edge1.voltmeter` and `edge.edge1.ammeter`
- The server's queue is bound to the exchange and any routing key that is a child of `edge` (`edge.#`), e.g., `edge.edge1.voltmeter`.

### AMQP broker
The docker container of the broker is based on the RabbitMQ image maintained by the docker community. We add additional setups to the configuration files in `/etc/rabbitmq/conf.d/` according to the [RabbitMQ documentation](https://www.rabbitmq.com/configure.html#config-confd-directory).

To deploy this image, use:
```bash
docker run -it --rm --name hp2c_broker -p 5672:5672 -p 15672:15672 hp2c/broker:latest
```

Ports 5672 (main AMQP port) and 15672 (RabbitMQ Management console) are reserved

To diagnose the RabbitMQ server:
```shell
rabbitmq-diagnostics status
```

### Additional configuration
- Timestamp is added to each message upon publishing using:
```
message_interceptors.incoming.set_header_timestamp.overwrite = false
```