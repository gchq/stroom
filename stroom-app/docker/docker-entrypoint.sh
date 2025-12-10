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

# Re-set permission to the `stroom` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'stroom' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
    # shellcheck disable=SC1091
    . /stroom/add_container_identity_headers.sh /stroom/logs/extra_headers.txt

    # change ownership of docker volume directories
    # WARNING: use chown -R with caution as some dirs (e.g. proxy-repo) can
    # contain MANY files, resulting in a big delay on container start
    chown stroom:stroom /stroom/content_pack_import
    chown stroom:stroom /stroom/lmdb
    chown stroom:stroom /stroom/lmdb_library
    chown stroom:stroom /stroom/logs
    chown stroom:stroom /stroom/logs/extra_headers.txt
    chown stroom:stroom /stroom/output
    chown stroom:stroom /stroom/reference_data
    chown stroom:stroom /stroom/search_results
    chown stroom:stroom /stroom/volumes

    # This is a bit of a cludge to get round "Text file in use" errors
    # See: https://github.com/moby/moby/issues/9547
    # sync ensures all disk writes are persisted
    sync

    #su-exec is the alpine equivalent of gosu
    #runs all args as user stroom, rather than as root
    exec su-exec stroom "$@"
fi

exec "$@"
