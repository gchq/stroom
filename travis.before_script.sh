#!/bin/bash

#exit script on any error
set -e

# shellcheck disable=SC2034
{
  #Shell Colour constants for use in 'echo -e'
  #e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Colour 
}

sudo bash -c "echo '127.0.0.1 kafka' >> /etc/hosts"
sudo bash -c "echo '127.0.0.1 hbase' >> /etc/hosts"
sudo bash -c "echo '127.0.0.1 stroom-auth-service' >> /etc/hosts"

echo -e "TRAVIS_EVENT_TYPE:   [${GREEN}${TRAVIS_EVENT_TYPE}${NC}]"

if [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    echo "Cron build so don't set up gradle plugins or docker containers"

else
    # Increase the size of the heap
    export JAVA_OPTS=-Xmx1024m
    echo -e "JAVA_OPTS: [${GREEN}$JAVA_OPTS${NC}]"

    # The version of compose that is ready installed with travis does not support v2.4 yml syntax
    # so we have to update to something more recent.
    echo -e "${GREEN}Removing docker-compose${NC}"
    sudo rm /usr/local/bin/docker-compose

    echo -e "${GREEN}Installing docker-compose ${BLUE}${DOCKER_COMPOSER_VERSION}${NC}"
    curl -L "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" > docker-compose
    chmod +x docker-compose
    sudo mv docker-compose /usr/local/bin

    echo -e "${GREEN}Clone our stroom-resources repo ${BLUE}${STROOM_RESOURCES_GIT_REF}${NC}"
    git clone https://github.com/gchq/stroom-resources.git
    pushd stroom-resources/bin
    git checkout "${STROOM_RESOURCES_GIT_REF}"

    echo -e "${GREEN}Start all the services we need to run the integration tests in stroom${NC}"
    ./bounceIt.sh 'up -d --build' -d -e -y -x kafka stroom-all-dbs zookeeper
    popd
    popd

    echo -e "${GREEN}Configure our plugins directory so stroom can find the kafka jar${NC}"
    mkdir -p ~/.stroom/plugins
    #Run the script to convert stroom.conf.template into stroom.conf
    ./stroom.conf.sh
fi

echo -e "${GREEN}Finished running ${BLUE}before_script${NC}"

exit 0
