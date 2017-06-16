#!/bin/bash

#run a build with no tests for speed
./buildNoTests.sh

docker build --tag=gchq/stroom:v6.0.0-SNAPSHOT --build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy stroom-app/.
