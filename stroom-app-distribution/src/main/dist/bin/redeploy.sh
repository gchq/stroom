#!/bin/bash

WAR_PREFIX="stroom-app"

export BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${BIN_DIR}/common.sh

stroom-echo "${INSTALL_DIR} - Re-Deploy"

# Script to monitor a war file to deploy if required
# Script also looks at stop start flags to control the cluster

stroom-try-lock

cd ${INSTALL_DIR}/lib
DEPLOY_WAR=`ls -1 ${WAR_PREFIX}*.war | java -cp "*" stroom.util.config.VersionSort | tail -1`
cd ${INSTALL_DIR}

if [ -f "lib/${DEPLOY_WAR}" ]; then
	stroom-echo "Deploy  WAR ${DEPLOY_WAR}"
else
	stroom-echo "Nothing to deploy as no WAR file located in lib"
	stroom-rm-lock
	exit 0	
fi

if [ -f "${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml" ]; then
	stroom-echo "Tomcat has been setup to run Stroom"
else
	stroom-echo "Tomcat has not yet been setup to run Stroom"
	stroom-rm-lock
	exit 0	
fi


RUNNING_WAR=`cat ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml | grep docBase | sed "s/.*\/lib\///g" | sed "s/'.*//g"`

if [ -f "lib/${RUNNING_WAR}" ]; then
	stroom-echo "Running WAR ${RUNNING_WAR}"
else
	stroom-echo "No running WAR "
fi

# Figure out if we need to stop (and thus start) tomcat

ORIGINAL_TOMCAT_PID=`jps -v | grep ${CATALINA_HOME} | cut -f1 -d' ' | tr '\n' ' '`

# Already Stopped
if [ -z "${ORIGINAL_TOMCAT_PID}" ]; then
	stroom-echo "Tomcat is already stopped.  No need to stop and then start"
else 
	./bin/stop.sh
fi


stroom-echo "Cleaning Tomcat"

rm -fr ${CATALINA_BASE}/work/*
rm -fr ${CATALINA_BASE}/webapps/*

stroom-echo "Installing ${DEPLOY_WAR} context in ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml"

echo "<?xml version='1.0' encoding='utf-8'?>" > ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml
echo "<Context docBase='${INSTALL_DIR}/lib/${DEPLOY_WAR}' path='/stroom' reloadable='true'/>" >> ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml


# Already Stopped
if [ -z "${ORIGINAL_TOMCAT_PID}" ]; then
	stroom-echo "Tomcat was not running so no need to start"
else 
	./bin/start.sh
fi

stroom-rm-lock
