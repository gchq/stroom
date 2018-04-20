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

sudo bash -c "echo '127.0.0.1 kafka' >> /etc/hosts"
sudo bash -c "echo '127.0.0.1 hbase' >> /etc/hosts"
sudo bash -c "echo '127.0.0.1 stroom-auth-service' >> /etc/hosts"

echo -e "TRAVIS_EVENT_TYPE:   [${GREEN}${TRAVIS_EVENT_TYPE}${NC}]"

if [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    echo "Cron build so don't set up gradle plugins or docker containers"

else
    echo -e "JAVA_OPTS: [${GREEN}$JAVA_OPTS${NC}]"
    # Increase the size of the heap
    export JAVA_OPTS=-Xmx1024m

    echo "Clone our stroom-resources repo"
    git clone https://github.com/gchq/stroom-resources.git
    pushd stroom-resources/bin
    git checkout $STROOM_RESOURCES_BRANCH

    echo "Start all the services we need to run the integration tests in stroom"
    ./bounceIt.sh 'up -d --build' -e -y -x kafka stroom-db stroom-stats-db zookeeper
    popd

    echo "Configure our plugins directory so stroom can find the kafka jar"
    mkdir -p ~/.stroom/plugins
    #Run the script to convert stroom.conf.template into stroom.conf
    ./stroom.conf.sh
fi

exit 0
