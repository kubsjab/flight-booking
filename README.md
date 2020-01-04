# Flight booking simulation 

[![Build Status](https://travis-ci.com/kubsjab/flight-booking.svg?token=7PK2EnfnQpj8Vgaz7vsF&branch=master)](https://travis-ci.com/kubsjab/flight-booking)

Flight booking simulation implemented using Actor programming model.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

What things you need to install the software and how to install them

```
- JDK 11
- SBT 1.1.6
- Docker 17.04
- Docker Compose 3.2
```

### Installing

In order to run simulation run following commands:

```
$ docker-compose up -d
$ chmod +x run_simulation.sh
$ ./run_simulation.sh # temporarily it contains SIMULATION_TYPE variable which should be changed
```

where `SIMULATION_TYPE` indicates which simulation should be run. Example usage with _standard_ simulation:

```
$ sbt "run standard"
```

## Deployment

Add additional notes about how to deploy this on a live system


## Authors

* **Jakub Jabloński** - [kubsjab](https://github.com/kubsjab)
* **Przemysław Wilczyński** - [pwilcz](https://github.com/pwilcz)
* **Michał Grzeszczyk** - [MiHu773](https://github.com/MiHu773)
