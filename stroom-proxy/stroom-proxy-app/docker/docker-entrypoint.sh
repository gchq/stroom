#!/bin/sh
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

HEADERS_FILE="/stroom-proxy/logs/extra_headers.txt"
# These should be set in the dockerfile, but just in case
USER_ID="${USER_ID:-1000}"
GROUP_ID="${GROUP_ID:-0}"

add_to_headers() {
    token="$1"
    value="$2"
    if [ -n "${value}" ]; then
        echo "${token}:${value}" >> "${HEADERS_FILE}"
    fi
}

create_headers_file() {
    # This command works in alpine linux 3.8, but may be fragile and subject to change
    # with different docker versions.
    container_id=
    if [ -f /proc/self/cgroup ]; then
      container_id="$( \
        grep -o -e "docker/.*" /proc/self/cgroup 2>/dev/null \
          | head -n 1 \
          | sed "s/docker\/\(.*\)/\\1/" \
      )" || true
    fi
    container_id="${container_id:-}"

    # Create the empty file
    : > "${HEADERS_FILE}"

    # Ensure anyone can read this file
    chmod ugo+r "${HEADERS_FILE}"

    # Write the details of the host of this container to the headers file
    # for stroom-log-sender

    # These two should be passed in as env vars at container run time
    add_to_headers "OriginalHost" "${DOCKER_HOST_HOSTNAME:-}"
    add_to_headers "OriginalIP" "${DOCKER_HOST_IP:-}"
    # This env var should be automatcially set from the build arg at container creation time
    add_to_headers "OriginalImageGitTag" "${GIT_TAG:-}"
    add_to_headers "OriginalContainerId" "${container_id:-}"

    echo "Dumping ${HEADERS_FILE} contents:"
    echo "-------------------------------------------"
    cat "${HEADERS_FILE}"
    echo "-------------------------------------------"
}

create_headers_file

# In case anyone tries to run the container as root drop down
if [ "$(id -u)" = '0' ]; then
    #su-exec is the alpine equivalent of gosu
    #runs all args as user USER_ID:GROUP_ID, rather than as root
    exec su-exec "$USER_ID:$GROUP_ID" "$@"
else
  # Already non-root so just crack on and run the cmd as is
  exec "$@"
fi
