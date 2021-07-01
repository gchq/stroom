#!/bin/sh
set -e

echo "PWD in container: $PWD"

# Re-set permission to the `stroom` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'stroom' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
  user="${CONTAINER_USER_ID}"

  echo "Using user id ${user}"

  # This is a bit of a cludge to get round "Text file in use" errors
  # See: https://github.com/moby/moby/issues/9547
  # sync ensures all disk writes are persisted
  #sync

  #su-exec is the alpine equivalent of gosu
  #runs all args as user stroom, rather than as root
  exec su-exec "${user}" "$@"
fi

exec "$@"
