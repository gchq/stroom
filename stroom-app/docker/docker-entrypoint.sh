#!/bin/sh
set -e

# Re-set permission to the `stroom` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'stroom' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
    chown -R stroom:stroom .
    
    # This is a bit of a cludge to get round "Text file in use" errors
    # See: https://github.com/moby/moby/issues/9547
    # sync ensures all disk writes are persisted
    sync

    # Build the crontab file to send logs to stroom
    echo "Creating the crontab file"
    ./create_crontab.sh

    # Start the cron daemon
    echo "Starting cron in the background"
    crond
    
    #su-exec is the alpine equivalent of gosu
    #runs all args as user stroom, rather than as root
    exec su-exec stroom "$@"
fi

exec "$@"
