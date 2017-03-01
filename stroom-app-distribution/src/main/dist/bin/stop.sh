#!/bin/bash

# Get and validate script params
WAIT=-1
if [[ $1 == "now" ]]; then
  WAIT=10
else
  if [[ $1 == "wait" ]]; then
    a=$2
    if [[ $a != "" && $a =~ ^-?[0-9]+$ ]]; then
        WAIT=$a
        if (( WAIT < 10 )); then
           echo "Wait time must be a greater than 10 seconds"
           exit 1
        fi
    else
        echo "Wait time must be a positive integer"
        exit 1
    fi
  fi
fi

export BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${BIN_DIR}/common.sh

stroom-init-check
stroom-get-lock

TOMCAT_PID=`jps -v | grep ${CATALINA_HOME} | cut -f1 -d' ' | tr '\n' ' '`
TOMCAT_INSTANCE_COUNT=`echo ${TOMCAT_PID} | wc -w`  


stroom-echo "${INSTALL_DIR} - Stop"

# Case 1 - Already Stopped
if [ -z "${TOMCAT_PID}" ]; then

	stroom-echo "Tomcat is already stopped"
	
	stroom-rm-lock
	exit 0
	
fi

# Case 2 - Multiple jvm's running - this should not happen but if it does we kill them all
if [ "${TOMCAT_INSTANCE_COUNT}" -gt 1 ]; then

	stroom-echo "Multiple matching Tomcats found will kill them all !! ${TOMCAT_PID}"
	kill -9 ${TOMCAT_PID}

	stroom-rm-lock
	exit 0
fi


stroom-echo "Stopping Tomcat pid ${TOMCAT_PID}"

# Kill any java options not needed for a stop
JAVA_OPTS=
${CATALINA_HOME}/bin/shutdown.sh &> /dev/null

TEST_RUNNING=`ps -p ${TOMCAT_PID} | grep ${TOMCAT_PID}`
COUNT=0

while [ -n "${TEST_RUNNING}" -a \( $COUNT -lt $WAIT -o $WAIT -lt 0 \) ]
do
        let COUNT++
        stroom-echo "Waiting for ${TOMCAT_PID} to stop. ${COUNT}"

        if (( COUNT >= WAIT && WAIT >= 0 )); then
                STACK_FILE=jstack_`date +%Y%m%d_%H%M%S`.txt
                stroom-echo "jstack ${TOMCAT_PID} > ${BIN_DIR}/${STACK_FILE}"
                jstack ${TOMCAT_PID} > ${BIN_DIR}/${STACK_FILE} &
                sleep 10
                stroom-echo "Tomcat failed to stop so kill -9 ${TOMCAT_PID}"
                kill -9 ${TOMCAT_PID}
        else
                sleep 1
                TEST_RUNNING=`ps -p ${TOMCAT_PID} | grep ${TOMCAT_PID}`
        fi
done

stroom-rm-lock
