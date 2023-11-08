

# HP2C-DT

Spain has set very ambitious energetic goals to achieve a fully sustainable, decarbonized and resilient energy system, as detailed in the [Plan Nacional Integrado de Energía y Clima (PNIEC) 2021-2030](https://www.miteco.gob.es/images/es/pnieccompleto_tcm30-508410.pdf). By 2030, 74% of power generation should come from renewable energy sources and the country must achieve emission neutrality by 2050, moving towards a 100% renewable energy electricity system. 

To achieve such ambitious renewable generation objectives, the [Plan España Digital (PED) 2025](https://www.lamoncloa.gob.es/presidente/actividades/Documents/2020/230720-Espa%C3%B1aDigital_2025.pdf) discusses a key fact, which is the blended evolution of both the energy and digital transition. PED-2025 discusses how digitalization can contribute to achieve a more resilient and clean economy, as a key step to secure the PNIEC defined objectives on reducing greenhouse gases effect by increasing renewable energy generation and energy efficiency increase.

This project embraces the digital and ecological transition to develop a High-Precision High-Performance Computer-enabled Digital Twin (HP2C-DT) for modern power system applications. The HP2C-DT project aims to develop an innovative network Digital Twin (DT) concept, able to represent with a high degree of fidelity modern power systems in a wide variety of applications, such as transmission, distribution, generation, railway and industrial networks. The main project goal is to maximize their resilience and real-time performance during the high-demanding and challenging energy transition, achieving 100% renewable power networks.


  ![concept](https://gitlab.bsc.es/wdc/projects/hp2cdt/-/raw/main/docs/figures/concept.png)

## Application Setup and Deployment
In our application, we use edge nodes responsible for collecting, processing, and transmitting data between various components of the electric grid. To distribute the computational load of the operations carried out by these edge nodes, we utilize them as COMPSs agents. Within these nodes, you can find various devices, including voltmeters, ammeters, and switches, among others. These devices will enable us to create small smart subgrids. Depending on the nature of the device and its desired utility, these devices can function as sensors, actuators, or both.

### Setup and communications
To configure an edge node, we use JSON files (refer to the "deployments" directory for examples). In this section, we will explain how to create them. An edge setup file will consist of three sections, as described below:

#### Devices
The "devices" section will contain each device within the edge. Each device has the following attributes:
1. "label": the name of the device, which must be unique within this edge.
2. "driver": the path to the device's implementation.
3. "position": the XY position.
4. "properties": We have only one property called "indexes," which serves as the device's identifier or identifiers. Voltmeters, ammeters, generators, wattmeters, and varmeters should have only one index. Three-phase voltmeters and three-phase ammeters should have three indexes, while switches can have one or three, depending on their number of phases. These indexes must be unique, as they define the correspondent position in the socket received.

#### Functions
A function is a method that can read from one or more sensors and actuate over one or more actuators. Within the "functions" section, the user must specify the following:
1. "label": a unique name for the function.
2. "lang": the language in which it was implemented (Java or Python).
3. "method_name": the path to the method's implementation.
4. "parameters": the user must define lists of "sensors", "actuators", and additional parameters called "others".
5. "trigger": the event that triggers the execution of the function. As for "type", it can be "onFrequency" (executed with a periodicity specified in "parameters") and "onRead" (executed after every read of the devices declared as sensors). Additionally, the user should provide a "method_name" (path to the implementation of those triggers).

#### Global Properties
This section contains global properties of the edge node, such as its "label" and its connection ports ("comms"). In "comms," the user should provide two objects:
1. "udp": UDP ports will be used for receiving data from the network (the edge will be listening through this port).
2. "tcp": TCP ports will be used for sending data to the network (the edge will be writing in this port).

Within these objects, the user must declare a pair port-list of devices. It is planned to allow introducing more than one pair, so that the user can choose which devices works with which ports, but is not implemented yet. At the moment, the list of devices will remain unused, and we will only check the key provided. These ports must be unique for every node.

### Deployment
To run the application, we provide an example using Docker containers named 'hp2c_' plus the label specified in 'global-properties'. In this case, each edge node is a different compss agent with ports starting in 4610 and ending with 1 (REST port) and 2 (COMM port). Anyways these ports will be shown through standard output. We are also using host network for communications with these containers. 

In order to test the application, it must be performed:
1. Create images using the script "docker/create_images.sh".
2. Run the created image. An example of a deployment script can be found in "deployments/testbed".
3. Send data. Recall that the declared indexes for each device represent the positions where the values of each device will be searched, so the socket sent must have at least size max(index) - min(index) + 1. There is an example of a random data server in "components/opalSimulator/scripts".




