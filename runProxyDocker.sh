#!/bin/bash

set -e

main() {
    local -r VOLUMES=( \
        stroom-proxy_config \
        stroom-proxy_content \
        stroom-proxy_logs \
        stroom-proxy_repo)

    docker rm stroom-proxy || echo "No container to delete"

    for volume in "${VOLUMES[@]}"; do
        docker volume rm $"{volume}" || echo "Volume ${volume} doesn't exist to delete"
    done

    for volume in "${VOLUMES[@]}"; do
        docker volume create "${volume}"
    done

    docker \
        run --name stroom-proxy \
        --mount source=stroom-proxy_config,target=/stroom-proxy/config/ \
        --mount source=stroom-proxy_content,target=/stroom-proxy/content/ \
        --mount source=stroom-proxy_logs,target=/stroom-proxy/logs/ \
        --mount source=stroom-proxy_repo,target=/stroom-proxy/repo/ \
        --publish 9090:9090 \
        --publish 9091:9091 \
        gchq/stroom-proxy:dev-SNAPSHOT
        #gchq/stroom-proxy:dev-SNAPSHOT bash -c "cat /stroom-proxy/state/config.yml"
}


