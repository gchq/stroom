#!/usr/bin/env bash
#
# Restarts Stroom

#source bin/utils.sh
#source config/scripts.env

  # shellcheck disable=SC1091
source stop.sh "$@"
  # shellcheck disable=SC1091
source start.sh "$@"
