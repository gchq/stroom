#!/usr/bin/env bash
#
# Shows Stroom's application logs

source bin/utils.sh
source config/scripts.env

tail -F ${PATH_TO_APP_LOG}
