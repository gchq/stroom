#!/bin/bash

# exit script on any error
set -eo pipefail

STROOM_DOCKER_REPO="gchq/stroom"
STROOM_PROXY_DOCKER_REPO="gchq/stroom-proxy"
STROOM_DOCKER_CONTEXT_ROOT="stroom-app/docker/."
STROOM_PROXY_DOCKER_CONTEXT_ROOT="stroom-proxy/stroom-proxy-app/docker/."
VERSION_FIXED_TAG=""
SNAPSHOT_FLOATING_TAG=""
MAJOR_VER_FLOATING_TAG=""
MINOR_VER_FLOATING_TAG=""
# This is a whitelist of branches to produce docker builds for
BRANCH_WHITELIST_REGEX='(^dev$|^master$|^[0-9]+\.[0-9]+$)'
RELEASE_VERSION_REGEX='^v[0-9]+\.[0-9]+.*$'
LATEST_SUFFIX="-LATEST"
# This is the branch containing the current release of stroom
# It is used to test which releases we push the swagger ui to ghpages for
# As 7 is still in beta, this is currently 6.1
CURRENT_STROOM_RELEASE_BRANCH="6.1"
doDockerBuild=false
STROOM_RESOURCES_DIR="${TRAVIS_BUILD_DIR}/stroom-resources" 
RELEASE_ARTEFACTS_DIR="${TRAVIS_BUILD_DIR}/release_artefacts"
RELEASE_MANIFEST="${RELEASE_ARTEFACTS_DIR}/release-artefacts.txt"
DDL_DUMP_DIR="${TRAVIS_BUILD_DIR}/build"
DDL_DUMP_FILE="${DDL_DUMP_DIR}/stroom-database-schema-${TRAVIS_TAG}.sql"

# Shell Colour constants for use in 'echo -e'
# e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
# shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Colour
}

create_file_hash() {
  local -r file="$1"
  local -r hash_file="${file}.sha256"
  local dir
  dir="$(dirname "${file}")"
  local filename
  filename="$(basename "${file}")"

  echo -e "Creating a SHA-256 hash for file ${GREEN}${filename}${NC} in ${GREEN}${dir}${NC}"
  # Go to the dir where the file is so the hash file doesn't contain the full
  # path
  pushd "${dir}" > /dev/null
  sha256sum "${filename}" > "${hash_file}"
  popd > /dev/null
  echo -e "Created hash file ${GREEN}${hash_file}${NC}, containing:"
  echo -e "-------------------------------------------------------"
  cat "${hash_file}"
  echo -e "-------------------------------------------------------"
}

stop_and_clear_down_stroom_all_dbs() {
  # clear down stroom-all-dbs container and volumes so we have a blank slate
  echo -e "${GREEN}Clearing down stroom-all-dbs${NC}"
  docker ps -q -f=name='stroom-all-dbs' | xargs -r docker stop --time 0
  docker ps -a -q -f=name='stroom-all-dbs' | xargs -r docker rm
  docker volume ls -q -f=name='bounceit_stroom-all-dbs*' | xargs -r docker volume rm
}

start_stroom_all_dbs() {

  if [[ ! -d "${STROOM_RESOURCES_DIR}" ]]; then
    echo -e "${GREEN}Clone our stroom-resources repo ${BLUE}${STROOM_RESOURCES_GIT_TAG}${NC}"

    git clone \
      --depth=1 \
      --branch "${STROOM_RESOURCES_GIT_TAG}" \
      --single-branch \
      https://github.com/gchq/stroom-resources.git \
      "${STROOM_RESOURCES_DIR}"
  fi

  pushd stroom-resources/bin > /dev/null

  # Increase the size of the heap
  #export JAVA_OPTS=-Xmx1024m
  #echo -e "JAVA_OPTS: [${GREEN}$JAVA_OPTS${NC}]"

  echo -e "${GREEN}Starting stroom-all-dbs in the background${NC}"
  ./bounceIt.sh \
    'up -d --build' \
    -y \
    -x \
    stroom-all-dbs

  popd > /dev/null
}

generate_ddl_dump() {
  mkdir -p "${DDL_DUMP_DIR}"

  stop_and_clear_down_stroom_all_dbs

  start_stroom_all_dbs

  # Run the db migration against the empty db to give us a vanilla
  # schema to dump
  # Assumes the app jar has been built already
  echo -e "${GREEN}Running DB migration on empty DB${NC}"
  "${TRAVIS_BUILD_DIR}/container_build/runInJavaDocker.sh" MIGRATE

  echo -e "${GREEN}Dumping the database DDL${NC}"
  # Produce the dump file
  docker exec \
    stroom-all-dbs \
    mysqldump \
      -d \
      -p"my-secret-pw" \
      stroom \
    > "${DDL_DUMP_FILE}"
}

generate_entity_rel_diagram() {
  # Needs the stroom-all-dbs container to be running and populated with a vanilla
  # database schema for us to generate an ERD from
  "${TRAVIS_BUILD_DIR}/container_build/runInJavaDocker.sh" ERD
}

copy_release_artefact() {
  local source="$1"; shift
  local dest="$1"; shift
  local description="$1"; shift

  echo -e "${GREEN}Copying release artefact ${BLUE}${source}${NC}"

  mkdir -p "${RELEASE_ARTEFACTS_DIR}"

  cp "${source}" "${dest}"

  local filename
  if [[ -f "${dest}" ]]; then
    filename="$(basename "${dest}")"
  else
    filename="$(basename "${source}")"
  fi

  # Add an entry to a manifest file for the release artefacts
  echo "${filename} - ${description}" \
    >> "${RELEASE_MANIFEST}"
}

# Put all release artefacts in a dir to make it easier to upload them to
# Github releases. Some of them are needed by the stack builds in
# stroom-resources
gather_release_artefacts() {
  mkdir -p "${RELEASE_ARTEFACTS_DIR}"

  local -r release_config_dir="${TRAVIS_BUILD_DIR}/stroom-app/build/release/config"
  local -r proxy_release_config_dir="${TRAVIS_BUILD_DIR}/stroom-proxy/stroom-proxy-app/build/release/config"

  local -r docker_build_dir="${TRAVIS_BUILD_DIR}/stroom-app/docker/build"
  local -r proxy_docker_build_dir="${TRAVIS_BUILD_DIR}/stroom-proxy/stroom-proxy-app/docker/build"

  echo "Copying release artefacts to ${RELEASE_ARTEFACTS_DIR}"

  # The zip dist config is inside the zip dist. We need the docker dist
  # config so stroom-resources can use it.

  # Stroom
  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/CHANGELOG.md" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "Change log for this release"

  copy_release_artefact \
    "${docker_build_dir}/config.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-app-config-${TRAVIS_TAG}.yml" \
    "Basic configuration file for stroom"

  copy_release_artefact \
    "${release_config_dir}/config-defaults.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-app-config-defaults-${TRAVIS_TAG}.yml" \
    "A complete version of Stroom's configuration with all its default values"

  copy_release_artefact \
    "${release_config_dir}/config-schema.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-app-config-schema-${TRAVIS_TAG}.yml" \
    "The schema for Stroom's configuration file"

  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/stroom-app/build/distributions/stroom-app-${TRAVIS_TAG}.zip" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The archive containing the Stroom application distribution"

  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/stroom-app/src/main/resources/ui/noauth/swagger/stroom.json" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The Swagger spec (in json form) for Stroom's API."

  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/stroom-app/src/main/resources/ui/noauth/swagger/stroom.yaml" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The Swagger spec (in yaml form) for Stroom's API."

  # Stroom-Proxy
  copy_release_artefact \
    "${proxy_docker_build_dir}/config.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-proxy-app-config-${TRAVIS_TAG}.yml" \
    "Basic configuration file for stroom-proxy"

  copy_release_artefact \
    "${proxy_release_config_dir}/config-defaults.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-proxy-app-config-defaults-${TRAVIS_TAG}.yml" \
    "A complete version of Stroom-Proxy's configuration with all its default values"

  copy_release_artefact \
    "${proxy_release_config_dir}/config-schema.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-proxy-app-config-schema-${TRAVIS_TAG}.yml" \
    "The schema for Stroom-Proxy's configuration file"

  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/stroom-proxy/stroom-proxy-app/build/distributions/stroom-proxy-app-${TRAVIS_TAG}.zip" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The archive containing the Stroom-Proxy application distribution"

  # Stroom (Headless)
  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/stroom-headless/build/distributions/stroom-headless-${TRAVIS_TAG}.zip" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The archive containing the Stroom-Headless application distribution"

  # Entity relationship diagram for the DB
  copy_release_artefact \
    "${TRAVIS_BUILD_DIR}/container_build/build/entity-relationships-${TRAVIS_TAG}.svg" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "An entity relationship diagram for the Stroom database"

  # DB DDL SQL
  copy_release_artefact \
    "${DDL_DUMP_FILE}" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The Stroom database schema SQL"

  # Now generate hashes for all the zips
  for file in "${RELEASE_ARTEFACTS_DIR}"/*.zip; do
    create_file_hash "${file}"
  done
}

# args: dockerRepo contextRoot tag1VersionPart tag2VersionPart ... tagNVersionPart
releaseToDockerHub() {
  # echo "releaseToDockerHub called with args [$@]"

  if [ $# -lt 3 ]; then
    echo "Incorrect args, expecting at least 3"
    exit 1
  fi
  dockerRepo="$1"
  contextRoot="$2"
  # shift the the args so we can loop round the open ended list of tags,
  # $1 is now the first tag
  shift 2

  local allTagArgs=()

  for tagVersionPart in "$@"; do
    if [ "x${tagVersionPart}" != "x" ]; then
      # echo -e "Adding docker tag [${GREEN}${tagVersionPart}${NC}]"
      allTagArgs+=("--tag=${dockerRepo}:${tagVersionPart}")
    fi
  done

  echo -e "Building a docker image with tags: ${GREEN}${allTagArgs[*]}${NC}"
  echo -e "dockerRepo:  [${GREEN}${dockerRepo}${NC}]"
  echo -e "contextRoot: [${GREEN}${contextRoot}${NC}]"
  # If we have a TRAVIS_TAG (git tag) then use that, else use the floating tag
  docker build \
    "${allTagArgs[@]}" \
    --build-arg GIT_COMMIT="${TRAVIS_COMMIT}" \
    --build-arg GIT_TAG="${TRAVIS_TAG:-${SNAPSHOT_FLOATING_TAG}}" \
    "${contextRoot}"

  if [[ ! -n "${LOCAL_BUILD}" ]]; then
    echo -e "Pushing the docker image to ${GREEN}${dockerRepo}${NC} with" \
      "tags: ${GREEN}${allTagArgs[*]}${NC}"
    docker push "${dockerRepo}" >/dev/null 2>&1
  else
    echo -e "${YELLOW}LOCAL_BUILD set so skipping docker push${NC}"
  fi

  echo -e "Completed Docker release"
}

docker_login() {
  echo -e "Logging in to Docker"
  # The username and password are configured in the travis gui
  if [[ ! -n "${LOCAL_BUILD}" ]]; then
    echo "$DOCKER_PASSWORD" \
      | docker login -u "$DOCKER_USERNAME" --password-stdin >/dev/null 2>&1
  else
    echo -e "${YELLOW}LOCAL_BUILD set so skipping docker login${NC}"
  fi
}


# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script proper starts here
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# establish what version of stroom we are building
if [ -n "$TRAVIS_TAG" ]; then
  # Tagged commit so use that as our stroom version, e.g. v6.0.0
  STROOM_VERSION="${TRAVIS_TAG}"
else
  # No tag so use the branch name as the version, e.g. dev
  STROOM_VERSION="${TRAVIS_BRANCH}"
fi

# Dump all the travis env vars to the console for debugging
echo -e "HOME:                          [${GREEN}${HOME}${NC}]"
echo -e "TRAVIS_BUILD_DIR:              [${GREEN}${TRAVIS_BUILD_DIR}${NC}]"
echo -e "TRAVIS_BUILD_NUMBER:           [${GREEN}${TRAVIS_BUILD_NUMBER}${NC}]"
echo -e "TRAVIS_COMMIT:                 [${GREEN}${TRAVIS_COMMIT}${NC}]"
echo -e "TRAVIS_BRANCH:                 [${GREEN}${TRAVIS_BRANCH}${NC}]"
echo -e "TRAVIS_TAG:                    [${GREEN}${TRAVIS_TAG}${NC}]"
echo -e "TRAVIS_PULL_REQUEST:           [${GREEN}${TRAVIS_PULL_REQUEST}${NC}]"
echo -e "TRAVIS_EVENT_TYPE:             [${GREEN}${TRAVIS_EVENT_TYPE}${NC}]"
echo -e "STROOM_VERSION:                [${GREEN}${STROOM_VERSION}${NC}]"
echo -e "CURRENT_STROOM_RELEASE_BRANCH: [${GREEN}${CURRENT_STROOM_RELEASE_BRANCH}${NC}]"
echo -e "docker version:                [${GREEN}$(docker --version)${NC}]"
echo -e "docker-compose version:        [${GREEN}$(docker-compose --version)${NC}]"

# Normal commit/PR/tag build
extraBuildArgs=()

if [ -n "$TRAVIS_TAG" ]; then
  doDockerBuild=true

  # This is a tagged commit, so create a docker image with that tag
  VERSION_FIXED_TAG="${TRAVIS_TAG}"

  # Extract the major version part for a floating tag
  majorVer=$(echo "${TRAVIS_TAG}" | grep -oP "^v[0-9]+")
  if [ -n "${majorVer}" ]; then
    MAJOR_VER_FLOATING_TAG="${majorVer}${LATEST_SUFFIX}"
  fi

  # Extract the minor version part for a floating tag
  minorVer=$(echo "${TRAVIS_TAG}" | grep -oP "^v[0-9]+\.[0-9]+")
  if [ -n "${minorVer}" ]; then
    MINOR_VER_FLOATING_TAG="${minorVer}${LATEST_SUFFIX}"
  fi

  if [[ "$TRAVIS_BRANCH" =~ ${RELEASE_VERSION_REGEX} ]]; then
    echo "This is a release version so add gradle arg for publishing" \
      "libs to Maven Central"
    # TODO need to add in the sonatype build args when we have decided 
    # what we are publishing from stroom
    #extraBuildArgs+=("bintrayUpload")
  fi
elif [[ "$TRAVIS_BRANCH" =~ $BRANCH_WHITELIST_REGEX ]]; then
  # This is a branch we want to create a floating snapshot docker image for
  SNAPSHOT_FLOATING_TAG="${STROOM_VERSION}-SNAPSHOT"
  doDockerBuild=true
fi

echo -e "VERSION FIXED DOCKER TAG:      [${GREEN}${VERSION_FIXED_TAG}${NC}]"
echo -e "SNAPSHOT FLOATING DOCKER TAG:  [${GREEN}${SNAPSHOT_FLOATING_TAG}${NC}]"
echo -e "MAJOR VER FLOATING DOCKER TAG: [${GREEN}${MAJOR_VER_FLOATING_TAG}${NC}]"
echo -e "MINOR VER FLOATING DOCKER TAG: [${GREEN}${MINOR_VER_FLOATING_TAG}${NC}]"
echo -e "doDockerBuild:                 [${GREEN}${doDockerBuild}${NC}]"
echo -e "extraBuildArgs:                [${GREEN}${extraBuildArgs[*]}${NC}]"

pushd "${TRAVIS_BUILD_DIR}" > /dev/null

# Login to docker so we have authenticated pulls that are not rate limited
docker_login

start_stroom_all_dbs

# Ensure we have a local.yml file as the integration tests will need it
./local.yml.sh

echo -e "${GREEN}Running all gradle builds with build version" \
  "${BLUE}${BUILD_VERSION}${NC}"

# Make this available to the gradle build which will get passed through
# into the docker container
export BUILD_VERSION="${STROOM_VERSION}"

# MAX_WORKERS env var should be set in travis settings to controll max
# gradle/gwt workers
./container_build/runInJavaDocker.sh GRADLE_BUILD

# Don't do a docker build for pull requests
if [ "$doDockerBuild" = true ] && [ "$TRAVIS_PULL_REQUEST" = "false" ] ; then
  # TODO - the major and minor floating tags assume that the release
  # builds are all done in strict sequence If say the build for v6.0.1 is
  # re-run after the build for v6.0.2 has run then v6.0-LATEST will point
  # to v6.0.1 which is incorrect, hopefully this course of events is
  # unlikely to happen
  allDockerTags=( \
    "${VERSION_FIXED_TAG}" \
    "${SNAPSHOT_FLOATING_TAG}" \
    "${MAJOR_VER_FLOATING_TAG}" \
    "${MINOR_VER_FLOATING_TAG}" \
  )

  # build and release stroom image to dockerhub
  releaseToDockerHub \
    "${STROOM_DOCKER_REPO}" \
    "${STROOM_DOCKER_CONTEXT_ROOT}" \
    "${allDockerTags[@]}"

  # build and release stroom-proxy image to dockerhub
  releaseToDockerHub \
    "${STROOM_PROXY_DOCKER_REPO}" \
    "${STROOM_PROXY_DOCKER_CONTEXT_ROOT}" \
    "${allDockerTags[@]}"

  echo -e "Logging out of Docker"
  docker logout >/dev/null 2>&1

  # Deploy the generated swagger specs and swagger UI (obtained from github)
  # to gh-pages
  if [ "$TRAVIS_BRANCH" = "${CURRENT_STROOM_RELEASE_BRANCH}" ]; then
    echo "Copying swagger-ui to gh-pages dir"
    ghPagesDir=$TRAVIS_BUILD_DIR/gh-pages
    swaggerUiCloneDir=$TRAVIS_BUILD_DIR/swagger-ui
    mkdir -p "${ghPagesDir}"
    # copy our generated swagger specs to gh-pages
    cp \
      "${TRAVIS_BUILD_DIR}"/stroom-app/src/main/resources/ui/swagger/swagger.* \
      "${ghPagesDir}/"
    # clone swagger-ui repo so we can get the ui html/js/etc

    git clone \
      --depth 1 \
      --branch "${SWAGGER_UI_GIT_TAG}" \
      --single-branch \
      https://github.com/swagger-api/swagger-ui.git \
      "${swaggerUiCloneDir}"

    # copy the bits of swagger-ui that we need
    cp -r "${swaggerUiCloneDir}"/dist/* "${ghPagesDir}"/
    # repalce the default swagger spec url in swagger UI
    sed \
      -i \
      's#url: ".*"#url: "https://gchq.github.io/stroom/swagger.json"#g' \
      "${ghPagesDir}/index.html"
  fi

  # If it is a tagged build copy all the files needed for the github release
  # artefacts
  if [ -n "$TRAVIS_TAG" ]; then
    generate_ddl_dump

    generate_entity_rel_diagram

    gather_release_artefacts
  fi
fi

exit 0

# vim:sw=2:ts=2:et:

