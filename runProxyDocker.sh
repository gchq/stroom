#!/bin/bash

set -e

docker rm stroom-proxy || echo "No container to delete"

docker volume rm stroom-proxy_state || echo "No volume to delete"

docker volume create stroom-proxy_state

docker \
    run --name stroom-proxy \
    --mount source=stroom-proxy_state,target=/stroom-proxy/state/ \
    --publish 9090:9090 \
    --publish 9091:9091 \
    gchq/stroom-proxy:dev-SNAPSHOT
    #gchq/stroom-proxy:dev-SNAPSHOT bash -c "cat /stroom-proxy/state/config.yml"

