#!/bin/sh
set -e

# Re-set permission to the `proxy` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'proxy' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
    . /stroom-proxy/add_container_identity_headers.sh /stroom-proxy/logs/extra_headers.txt

    # change ownership of docker volume directories
    chown proxy:proxy /stroom-proxy/config
    chown proxy:proxy /stroom-proxy/content
    chown proxy:proxy /stroom-proxy/logs
    chown proxy:proxy /stroom-proxy/logs/extra_headers.txt
    chown proxy:proxy /stroom-proxy/repo

    # This is a bit of a cludge to get round "Text file in use" errors
    # See: https://github.com/moby/moby/issues/9547
    # sync ensures all disk writes are persisted
    sync

    #su-exec is the alpine equivalent of gosu
    #runs all args as user proxy, rather than as root
    exec su-exec proxy "$@"
fi

exec "$@"
