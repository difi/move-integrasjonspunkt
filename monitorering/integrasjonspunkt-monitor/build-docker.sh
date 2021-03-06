#!/bin/sh
# author: Dervis M
# Builds an image and creates a container.

# Set current execution dir to this folder
cd $(cd -P -- "$(dirname -- "$0")" && pwd -P)

# Build params
IMAGE_NAME=difi/monitorui
CONTAINER_NAME=Difi_MonitorUI
echo ${CONTAINER_NAME}

# Must stop any running container to continue
docker stop ${CONTAINER_NAME}

# Must be the root folder
WORKING_DIR=$(pwd)
PORT=$1

echo "Working dir: $WORKING_DIR"

if [ -z "$1" ]; then
  echo "You have to specify a port number. Format ./build-docker.sh portNumber"
  exit
fi

# Remove any existing container
OLD_CONTAINERS=$(docker ps -a | grep ${IMAGE_NAME})
if [ "$OLD_CONTAINERS" != "" ]; then
  echo "Removing old containers:"
  docker rm $(docker ps -a | grep ${IMAGE_NAME} | awk '{print $1}')
fi

# Remove all unused images
OLD_IMAGES=$(docker images | grep '<none>')
if [ "$OLD_IMAGES" != "" ]; then
  echo "Removing old images:"
  docker rmi $(docker images | grep '<none>' | awk '{print $3}')
fi

# Build new image
docker build --no-cache -t ${IMAGE_NAME} . &&\

# Create new container
docker create --name ${CONTAINER_NAME} --link Difi_SpringBootEurekaServer:eurekaserver -p ${PORT}:8090 ${IMAGE_NAME}

# Done
echo "$CONTAINER_NAME is build. Starting the container."
echo "To see log output, run docker logs -f $CONTAINER_NAME (CTRL+C to exit logs)."
echo

docker start $CONTAINER_NAME
