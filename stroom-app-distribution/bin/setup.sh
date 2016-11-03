#!/bin/bash

export BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${BIN_DIR}/common.sh

stroom-echo "Make sure any existing install is stopped"
./bin/stop.sh

WAR_PREFIX="stroom-app"

stroom-echo "Removing any existing install instance"

rm -fr instance/*

stroom-echo "Creating install instance"

rm -fr instance
mkdir instance
cp -r template/* instance/
mkdir instance/temp 

if [ ! -f bin/~setup.xml ]; then
	cp bin/setup.xml bin/~setup.xml
fi
cp bin/env.sh bin/~env.sh

JARS=`find lib/ -name '*.jar'`

if [ -z "${JARS}" ]; then
	stroom-echo "No jars in lib dir? (Maybe you need to run ./bin/dev-setup-lib.sh)"
	exit 0
fi


stroom-echo "Configure parameters"

if [ -n "$IN_DOCKER" ];
then 
	java -cp "lib/*" stroom.util.config.Configure parameterFile=bin/~setup.xml readParameter=false processFile=instance/conf/server.xml,instance/lib/stroom.properties,bin/~env.sh;
else 
	java -cp "lib/*" stroom.util.config.Configure parameterFile=bin/~setup.xml processFile=instance/conf/server.xml,instance/lib/stroom.properties,bin/~env.sh;
fi

JAVA_CODE=$?

if [ ! ${JAVA_CODE} -eq 0 ]; then
	echo ""
	echo ""
	stroom-echo "Quit!! ${JAVA_CODE}"
	exit ${JAVA_CODE}
fi
stroom-echo "Installing MySQL Driver"

cp lib/mysql*.jar instance/lib

cd lib
DEPLOY_WAR=`ls -1 ${WAR_PREFIX}*.war | java -cp "*" stroom.util.config.VersionSort | tail -1`
cd ${DIR}/..


stroom-echo "Create any missing required dirs as they are empty"
mkdir -p ${CATALINA_BASE}/conf/Catalina/localhost
mkdir -p ${CATALINA_BASE}/logs


stroom-echo "Installing ${DEPLOY_WAR} context in ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml"

echo "<?xml version='1.0' encoding='utf-8'?>" >  ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml
echo "<Context docBase='${INSTALL_DIR}/lib/${DEPLOY_WAR}' path='/stroom' reloadable='true'>" >> ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml
echo "</Context>" >> ${CATALINA_BASE}/conf/Catalina/localhost/stroom.xml

stroom-echo "Finished"



 


