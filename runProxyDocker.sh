#!/bin/bash

#
# Copyright 2016-2025 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

main() {
    local -r VOLUMES=( \
        stroom-proxy_config \
        stroom-proxy_content \
        stroom-proxy_logs \
        stroom-proxy_data)

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
        --mount source=stroom-proxy_data,target=/stroom-proxy/data/ \
        --publish 9090:9090 \
        --publish 9091:9091 \
        gchq/stroom-proxy:dev-SNAPSHOT
        #gchq/stroom-proxy:dev-SNAPSHOT bash -c "cat /stroom-proxy/state/config.yml"
}


