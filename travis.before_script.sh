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

echo -e "LOCAL_BUILD:                   [${GREEN}${LOCAL_BUILD}${NC}]"
echo -e "STROOM_RESOURCES_GIT_TAG       [${GREEN}${STROOM_RESOURCES_GIT_TAG}${NC}]"

if [[ ! -n "${LOCAL_BUILD}" ]]; then

  sudo bash -c "echo '127.0.0.1 kafka' >> /etc/hosts"
  sudo bash -c "echo '127.0.0.1 hbase' >> /etc/hosts"

  # Do docker login here so all pulls are authenticated and thus are
  # not hit by the un-authenticated rate-limit
  echo -e "Logging in to Docker"
  # The username and password are configured in the travis gui
  echo "$DOCKER_PASSWORD" \
    | docker login -u "$DOCKER_USERNAME" --password-stdin >/dev/null 2>&1

  # The version of compose that is ready installed with travis does not support v2.4 yml syntax
  # so we have to update to something more recent.
  echo -e "${GREEN}Removing docker-compose${NC}"
  sudo rm /usr/local/bin/docker-compose

  echo -e "${GREEN}Installing docker-compose ${BLUE}${DOCKER_COMPOSER_VERSION}${NC}"
  curl -L "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" > docker-compose
  chmod +x docker-compose
  sudo mv docker-compose /usr/local/bin
else
  echo -e "${YELLOW}LOCAL_BUILD set so skipping compose install${NC}"
fi

echo -e "${GREEN}Clone our stroom-resources repo ${BLUE}${STROOM_RESOURCES_GIT_TAG}${NC}"
git clone \
  --depth=1 \
  --branch "${STROOM_RESOURCES_GIT_TAG}" \
  --single-branch \
  https://github.com/gchq/stroom-resources.git

pushd stroom-resources/bin

# Increase the size of the heap
export JAVA_OPTS=-Xmx1024m
echo -e "JAVA_OPTS: [${GREEN}$JAVA_OPTS${NC}]"

echo -e "${GREEN}Start all the services we need to run the integration tests in stroom${NC}"
./bounceIt.sh \
  'up -d --build' \
  -y \
  -x \
  stroom-all-dbs

  #kafka \
  #zookeeper

popd

if [[ ! -n "LOCAL_BUILD" ]]; then
  echo -e "Logging out of Docker"
  docker logout >/dev/null 2>&1
else
  echo -e "${YELLOW}LOCAL_BUILD set so skipping docker logout${NC}"
fi

echo -e "${GREEN}Finished running ${BLUE}before_script${NC}"

exit 0

# vim:sw=2:ts=2:et:

