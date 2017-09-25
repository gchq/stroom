#!/bin/bash

#run a build with no tests for speed
./buildNoIntTests.sh

#define the docker context as stroom-app/docker as this is where all the docker artefacts are, including the dockerfile
docker build --tag=gchq/stroom:v6.0.0-LOCAL-SNAPSHOT --build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy stroom-app/docker/.
