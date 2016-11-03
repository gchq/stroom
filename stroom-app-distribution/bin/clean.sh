#!/bin/bash

export BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${BIN_DIR}/common.sh

stroom-echo "${INSTALL_DIR} - Clean"

stroom-try-lock

if [ ! -z "${CATALINA_BASE}" ]; then
	
	if [ -d ${CATALINA_BASE} ]; then
	
		stroom-delete ${CATALINA_BASE}/logs 5
		stroom-delete ${CATALINA_BASE}/temp 1
		
	fi

fi

stroom-rm-lock
