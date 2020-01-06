#!/bin/bash

SIMULATION_TYPE=standard
#SIMULATION_TYPE=delayed
#SIMULATION_TYPE=overbooking

export DB_NAME=$SIMULATION_TYPE'_db'
export CONTAINER_NAME="flight-booking-db"
export SBT_OPTS="-Dslick.db.dbname=$DB_NAME"

eval "./init_db.sh $DB_NAME"


sbt "run $SIMULATION_TYPE"
