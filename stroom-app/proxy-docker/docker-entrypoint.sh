#!/bin/sh
set -e

# Re-set permission to the `proxy` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'proxy' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
    chown -R proxy:proxy .
    #su-exec is the alpine equivalent of gosu
    #runs all args as user proxy, rather than as root
    exec su-exec proxy "$@"
fi

exec "$@"
