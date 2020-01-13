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
#### Run simulation
In order to run simulation run following commands:

```
$ docker-compose up -d
$ chmod +x run_simulation.sh
$ ./run_simulation.sh #  it contains SIMULATION_TYPE variable which should be changed
```

where variable `SIMULATION_TYPE` defined in `run_simulation.sh` script indicates which simulation should be run. Available options: `standard`, `delayed`, `overbooking`

#### See results
##### Set up database connection
In order to upload results to prepared Excel SpreadSheet u need to define 3 OBDC Data Sources that will allow 
connection to our PostgreSQL databases, where results are stored. PostgreSQL OBDC driver can be obtained [here](https://www.postgresql.org/ftp/odbc/versions/).

Below is a table with datasource name and database it should connect to in order to allow Excel to download data.

| Data Source Name 	| Database name 	|
|-----------------	|--------------	|
|     sim_standard 	|      standard 	|
|      sim_delayed 	|       delayed 	|
|  sim_overbooking 	|   overbooking 	|

To connect to our database running on docker on local machine use 

|                       |               |
|-------------------	|-----------	|
| host              	| localhost 	|
| port              	| 5432      	|
| database username 	| docker    	|
| database password 	| docker    	|

##### Update results in spreadsheet
To refresh views and download newest results open Data tab and click Refresh All in Queries & Connections group.
Detailed instruction how to do it can be found [here](https://support.office.com/en-us/article/refresh-an-external-data-connection-in-excel-1524175f-777a-48fc-8fc7-c8514b984440)


## Authors

* **Jakub Jabloński** - [kubsjab](https://github.com/kubsjab)
* **Przemysław Wilczyński** - [pwilcz](https://github.com/pwilcz)
* **Michał Grzeszczyk** - [MiHu773](https://github.com/MiHu773)
