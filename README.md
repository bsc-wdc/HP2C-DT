# HP2C-DT: High-Precision High-Performance Computer-enabled Digital Twin

This repository contains the reference implementation of **HP2C-DT** (High-Precision High-Performance Computer-enabled Digital Twin), a flexible digital-twin framework for the computing continuum. The software enables high-fidelity, distributed simulations integrating edge devices, real-time data exchange, and HPC-enabled workflows.

This codebase corresponds to the implementation described in the manuscript:

> *HP2C-DT: High-Precision High-Performance Computer-enabled Digital Twin*, Future Generation Computer Systems, 2025, https://doi.org/10.1016/j.future.2025.108333.

![concept](docs/figures/concept.png)

## Repository structure

The repository is organized as follows:

- `apps/`: Application-specific modules, including dataset generation (`dataset_gen/`) and optimization tools (`optimization/`)
- `components/`: Core system components:
  - `broker/`: Message broker configuration and scripts
  - `common/`: Shared Java libraries and utilities
  - `database/`: Database configuration (InfluxDB)
  - `edge/`: Edge node implementation
  - `opalSimulator/`: OPAL simulator integration
  - `server/`: Server-side logic
  - `udsServer/`: UDS server implementation for Python interaction
  - `userInterface/`: Web-based user interface (Django application)
- `deployments/`: Deployment scripts and configuration files for various scenarios
- `docker/`: Dockerfiles and compose files for containerized deployment
- `docs/`: Documentation files, including setup and usage guides
- `experiments/`: Experimental setups and results
- `tests/`: Unit testing

## Dependencies

The project uses a multi-language stack with containerized deployment. Key dependencies include:

- Docker for containerized deployment and running services
- Java 8+ and Maven for building Java-based components
- Python 3.x for Python-based components (e.g., user interface and domain-specific functions)
- InfluxDB for the time-series database
- RabbitMQ as the message broker (provided via Docker image)
- Grafana is used to visualize time-series data and monitor system status

Refer to individual component READMEs or `pom.xml`/`requirements.txt` files for version details.

## Quick Start

A minimal local deployment for testing and exploration can be executed using Docker:

```bash
./deployments/deploy_all.sh
```

This launches a broker, server, user interface, database, and simulator on a single machine provided all dependencies and requirements are met. For distributed or production-like deployments, see the detailed guides below.

## Documentation

Comprehensive documentation is provided below:

- [Core components and features](docs/OVERALL_DESCRIPTION.md)
- [Startup and basic usage](docs/STARTUP.md)
- [Remote server and infrastructure setup](docs/SERVER_SETUP.md)
- [OpenStack port configuration guide](docs/SERVER_COMM.md)
- [User interface and workflow execution](components/userInterface/README.md)

These documents describe configuration files, communication protocols, functions, workflows, and deployment scenarios in detail.

## Citation

If you use this software, please cite the article describing the HP2C-DT framework and its reference implementation:

> Iraola, E., García-Lorenzo, M., Lordan-Gomis, F., Rossi, F., Prieto-Araujo, E., & Badia, R. M. (2025). *HP2C-DT: High-Precision High-Performance Computer-enabled Digital Twin*. **Future Generation Computer Systems**, 108333. https://doi.org/10.1016/j.future.2025.108333

## Acknowledgements

This work has been supported by the HP2C-DT TED2021-130351B-C22, HP2C-DT TED2021-130351B-C21 and PID2023-147979NB-C21 projects, funded by the MCIN/AEI/10.13039/501100011033, the European Union NextGenerationEU/ PRTR, and by the Departament de Recerca i Universitats de la Generalitat de Catalunya, research group MPiEDist (2021 SGR 00412). Furthermore, Eduardo Iraola acknowledges his AI4S fellowship within the "Generación D" initiative by Red.es, Ministerio para la Transformación Digital y de la Función Pública, for talent attraction (C005/24-ED CV1), funded by NextGenerationEU through PRTR.