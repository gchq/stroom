#!/bin/bash

export BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${BIN_DIR}/common.sh

stroom-echo "${INSTALL_DIR} - Start"

# Source instance specific env.sh 
if [ -f "${BIN_DIR}/~env.sh" ]; then
	stroom-echo "Sourcing ${BIN_DIR}/~env.sh"
	. ${BIN_DIR}/~env.sh
fi

# Source user specific env.sh 
if [ -f "${HOME}/env.sh" ]; then
	stroom-echo "Sourcing ${HOME}/env.sh"
	. ${HOME}/env.sh
fi

stroom-init-check
stroom-get-lock

TOMCAT_PID=`jps -v | grep ${CATALINA_HOME} | cut -f1 -d' ' | tr '\n' ' '`

if [ -z "${TOMCAT_PID}" ]; then

        stroom-echo "Tomcat is not running.... Good"

else

        stroom-echo "Sorry but Tomcat is already running. pid ${TOMCAT_PID}"
        stroom-rm-lock
        exit 0
fi

stroom-echo "Starting Tomcat - Using JAVA_OPTS=${JAVA_OPTS}"

nohup ${CATALINA_HOME}/bin/debug.sh &> /dev/null

TOMCAT_PID=""
RETRY=10
while [ -z "${TOMCAT_PID}" -a "${RETRY}" -gt 0 ]
do
        stroom-echo "Waiting for ${CATALINA_HOME} to start. ${RETRY}"
        sleep 1
        TOMCAT_PID=`jps -v | grep ${CATALINA_HOME} | cut -f1 -d' ' | tr '\n' ' '`
        RETRY=$(($RETRY -1))
done

stroom-echo "New Tomcat pid ${TOMCAT_PID}"

stroom-rm-lock
