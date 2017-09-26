#!/bin/sh
set -e

# Re-set permission to the `stroom` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'stroom' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
chown -R stroom .
    #su-exec is the alpine equivalent of gosu
    #runs all args as user stroom, rather than as root
    exec su-exec stroom "$@"
fi

exec "$@"
