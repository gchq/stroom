#!/usr/bin/env bash
#
# Shows Stroom's application logs

source bin/utils.sh

tail -F ${PATH_TO_APP_LOG}
