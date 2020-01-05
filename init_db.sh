#!/bin/bash

if [ -z ${DB_NAME+x} ];
then
    DB_NAME="postgres"
fi

if [ -z ${CONTAINER_NAME+x} ];
then
    CONTAINER_NAME="flight-booking-db"
fi

DB_USERNAME=docker

docker cp ./initdb/schema.sql $CONTAINER_NAME:/docker-entrypoint-initdb.d/schema.sql
docker exec $CONTAINER_NAME psql --username=$DB_USERNAME --dbname=$DB_NAME -f docker-entrypoint-initdb.d/schema.sql