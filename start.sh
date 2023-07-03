#!/bin/bash -e

docker-compose stop
docker-compose rm

cd produtor
mvn clean package
docker build -t produtor .
cd ..

cd consumidor
mvn clean package
docker build -t consumidor .
cd ..

cd starvation-finder
mvn clean package
docker build -t starvation-finder .
cd ..

docker-compose up -d