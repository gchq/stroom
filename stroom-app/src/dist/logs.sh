#!/usr/bin/env bash
#
# Shows Stroom's application logs

# shellcheck disable=SC1091
source bin/utils.sh
# shellcheck disable=SC1091
source config/scripts.env

tail -F "${PATH_TO_APP_LOG}"

# vim:sw=2:ts=2:et:
