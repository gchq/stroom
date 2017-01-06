# Common script 


cd ${BIN_DIR}/..

export INSTALL_DIR=`pwd`
export CATALINA_HOME=${INSTALL_DIR}/apache-tomcat-7.0.53
export CATALINA_BASE=${INSTALL_DIR}/instance


CMD=$0

# LCK_FILE=/tmp/`basename $0`.lck
FULL_PATH=$BIN_DIR/`basename $0`
PATH_HASH=`echo $FULL_PATH | md5sum | awk '{print $1}'`
LCK_FILE=/tmp/$PATH_HASH.lock

THIS_PID=`echo $$`

stroom-echo() {
	ECHO_TIME=`date +"%Y-%m-%dT%H:%M:%S.000"`
	echo "${ECHO_TIME} ${CMD} ${1}"
}


# Lock for the script if we fail
stroom-init-check() {
	if [ -z "${JAVA_HOME}" ]; then
		stroom-echo "No JAVA_HOME set"
	    exit 0
	fi
	if [ ! -f "${JAVA_HOME}/bin/java" ]; then
		stroom-echo "${JAVA_HOME}/bin/java does not exist"
	    exit 0
    fi
}


# Utility to delete old files etc  
stroom-delete() {
	if [ "$1" -a "$2" ]; then
		stroom-echo "Cleaning Dir $1 with files older than $2 days"
		if [ -n $1 -a -d $1 ]; then
			find ${1} -type f -mtime +${2} -exec rm {} \;
		else
			stroom-echo "Dir $1 does not exist!"
		fi
	else
		stroom-echo "Error stroom-delete expecting 2 arguments"
	fi
}

# Lock for the script and warn if we fail
stroom-get-lock() {
	if [ -f "${LCK_FILE}" ]; then
	        MYPID=`head -n 1 "${LCK_FILE}"`
	        TEST_RUNNING=`ps -p ${MYPID} | grep ${MYPID}`
	
	        if [ -z "${TEST_RUNNING}" ]; then
	                stroom-echo "Obtained lock for ${THIS_PID}"
	                echo "${THIS_PID}" > "${LCK_FILE}"
	        else
	                stroom-echo "Sorry ${CMD} is already running[${MYPID}]"
	                exit 0
	        fi
	else
	        stroom-echo "Obtained lock for ${THIS_PID} in ${LCK_FILE}"
	        echo "${THIS_PID}" > "${LCK_FILE}"
	fi
}

# Lock for the script and warn if we fail
stroom-try-lock() {
	if [ -f "${LCK_FILE}" ]; then
	        MYPID=`head -n 1 "${LCK_FILE}"`
	        TEST_RUNNING=`ps -p ${MYPID} | grep ${MYPID}`
	
	        if [ -z "${TEST_RUNNING}" ]; then
	                stroom-echo "Obtained lock for ${THIS_PID}"
	                echo "${THIS_PID}" > "${LCK_FILE}"
	        else
	                stroom-echo "Sorry ${CMD} is already running[${MYPID}]"
	                exit 0
	        fi
	else
	        stroom-echo "Obtained lock for ${THIS_PID} in ${LCK_FILE}"
	        echo "${THIS_PID}" > "${LCK_FILE}"
	fi
}

stroom-rm-lock() {
	if [ -f ${LCK_FILE} ]; then
    	stroom-echo "Removed lock ${LCK_FILE} for ${THIS_PID}"
		rm -f ${LCK_FILE}
	fi
}
