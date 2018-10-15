#!/usr/bin/env bash
#
# Tells the user if stroom is running or not.

source bin/utils.sh

readonly NOT_RUNNING_MESSAGE="Stroom is not running"
readonly RUNNING_MESSAGE="Stroom is running under pid"

if [ ! -f ${PID_FILE} ]; then # If there is no pid file
  info "$NOT_RUNNING_MESSAGE"
else # If there is a pid file we need to deal with it
  PID=$(cat $PID_FILE);

  if [ "$PID" = '' ]; then # If the pid file is empty for some reason
    info "$NOT_RUNNING_MESSAGE"
  else 
    if ps -p $PID > /dev/null
      then
      info "$RUNNING_MESSAGE $PID"
    else
      warn "Stroom was running as $PID but it looks like it stopped. You may want to check the logs to see what happened."
    fi
  fi
fi
