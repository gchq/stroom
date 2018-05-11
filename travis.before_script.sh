#!/bin/bash

#exit script on any error
set -e

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
NC='\033[0m' # No Colour 

echo -e "TRAVIS_EVENT_TYPE:   [${GREEN}${TRAVIS_EVENT_TYPE}${NC}]"

if [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    echo "Cron build so don't set up gradle plugins or docker containers"

else
    echo -e "JAVA_OPTS: [${GREEN}$JAVA_OPTS${NC}]"
    # Increase the size of the heap
    export JAVA_OPTS=-Xmx1024m

    echo "Clone our event-logging repo"
    git clone https://github.com/gchq/event-logging.git
    pushd event-logging
    git checkout 3.0
    echo "Building event-logging"
    mvn clean install
    popd

    echo "Clone our stroom-resources repo"
    git clone https://github.com/gchq/stroom-resources.git
    pushd stroom-resources/bin
    git checkout $STROOM_RESOURCES_BRANCH

    echo "Start all the services we need to run the integration tests in stroom"
    ./bounceIt.sh 'up -d --build' -e -y -x stroom-db stroom-stats-db
    popd
fi

exit 0
