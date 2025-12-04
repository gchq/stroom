#!/bin/bash

#
# Copyright 2016-2025 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This is the CI build script that is run by Github Actions and does the
# following:
#   * Start up a DB to run tests against
#   * Compile the app and run all the tests
#   * Build the app jars and distribution zips
#   * Build the docker images
#   If this is a push on a release branch, e.g. '7.0':
#     * Push the docker images with floating tags, e.g.  '7-LATEST' '7.0-LATEST'
#   If this is a tagged release it will also:
#     * Build an entity relationship diagram
#     * Build a SQL DDL script for the DB
#     * Gather all release artefacts into one place
#     * Push the docker images
#     * Create a Github release and add all the artefacts

# Dependencies for this script:
#   * bash + standard shell tools (sed, grep, etc.)
#   * docker
#   * docker-compose

# The actual build is run inside a docker container which has all the
# dependencies for performing the build and will spawn other docker
# containers to perform sub parts of the build, e.g. the UI build.

# Lines like:
# echo "::group::DDL dump"
# are to group/collapse shell output in the Github actions console

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
# This is the branch containing the current stable release of stroom
# It is used to determine which releases we push the swagger ui to ghpages for
# As 7 is still in beta, this is currently 6.1

# The version of stroom-resources used for running the DB, should be a tag really
STROOM_RESOURCES_GIT_TAG="7.7-stroom-7.0-proxy"
SWAGGER_UI_GIT_TAG="v3.49.0"
doDockerBuild=false
STROOM_RESOURCES_DIR="${BUILD_DIR}/stroom-resources"
RELEASE_ARTEFACTS_DIR="${BUILD_DIR}/release_artefacts"
RELEASE_MANIFEST="${RELEASE_ARTEFACTS_DIR}/release-artefacts.txt"
DDL_DUMP_DIR="${BUILD_DIR}/build"
DDL_DUMP_FILE="${DDL_DUMP_DIR}/stroom-database-schema-${BUILD_TAG}.sql"

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

start_databases() {
  local dbs_to_start=( "$@" )

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

  echo -e "${GREEN}Starting [${dbs_to_start[*]}] in the background${NC}"
  ./bounceIt.sh \
    'up -d --build' \
    -y \
    -x \
    "${dbs_to_start[@]}"

  popd > /dev/null
}

generate_ddl_dump() {
  echo "::group::DDL dump"
  mkdir -p "${DDL_DUMP_DIR}"

  stop_and_clear_down_stroom_all_dbs

  start_databases stroom-all-dbs

  # Run the db migration against the empty db to give us a vanilla
  # schema to dump
  # Assumes the app jar has been built already
  echo -e "${GREEN}Running DB migration on empty DB${NC}"
  "${BUILD_DIR}/container_build/runInJavaDocker.sh" MIGRATE

  echo -e "${GREEN}Dumping the database DDL${NC}"
  # Produce the dump file
  docker exec \
    stroom-all-dbs \
    mysqldump \
      -d \
      -p"my-secret-pw" \
      stroom \
    > "${DDL_DUMP_FILE}"
  echo "::endgroup::"
}

generate_entity_rel_diagram() {

  echo "::group::ERD generation"
  # Needs the stroom-all-dbs container to be running and populated with a vanilla
  # database schema for us to generate an ERD from
  "${BUILD_DIR}/container_build/runInJavaDocker.sh" ERD
  echo "::endgroup::"
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
  echo "::group::Gather release artefacts"
  mkdir -p "${RELEASE_ARTEFACTS_DIR}"

  local -r release_config_dir="${BUILD_DIR}/stroom-app/build/release/config"
  local -r proxy_release_config_dir="${BUILD_DIR}/stroom-proxy/stroom-proxy-app/build/release/config"

  local -r docker_build_dir="${BUILD_DIR}/stroom-app/docker/build"
  local -r proxy_docker_build_dir="${BUILD_DIR}/stroom-proxy/stroom-proxy-app/docker/build"

  echo "Copying release artefacts to ${RELEASE_ARTEFACTS_DIR}"

  # The zip dist config is inside the zip dist. We need the docker dist
  # config so stroom-resources can use it.

  # Stroom
  copy_release_artefact \
    "${BUILD_DIR}/CHANGELOG.md" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "Change log for this release"

  copy_release_artefact \
    "${docker_build_dir}/config.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-app-config-${BUILD_TAG}.yml" \
    "Basic configuration file for stroom"

  copy_release_artefact \
    "${release_config_dir}/config-defaults.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-app-config-defaults-${BUILD_TAG}.yml" \
    "A complete version of Stroom's configuration with all its default values"

  copy_release_artefact \
    "${release_config_dir}/config-schema.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-app-config-schema-${BUILD_TAG}.yml" \
    "The schema for Stroom's configuration file"

  copy_release_artefact \
    "${BUILD_DIR}/stroom-app/build/distributions/stroom-app-${BUILD_TAG}.zip" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The archive containing the Stroom application distribution"

  copy_release_artefact \
    "${BUILD_DIR}/stroom-app/src/main/resources/ui/noauth/swagger/stroom.json" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The Swagger spec (in json form) for Stroom's API."

  copy_release_artefact \
    "${BUILD_DIR}/stroom-app/src/main/resources/ui/noauth/swagger/stroom.yaml" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The Swagger spec (in yaml form) for Stroom's API."

  # Stroom-Proxy
  copy_release_artefact \
    "${proxy_docker_build_dir}/config.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-proxy-app-config-${BUILD_TAG}.yml" \
    "Basic configuration file for stroom-proxy"

  copy_release_artefact \
    "${proxy_release_config_dir}/config-defaults.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-proxy-app-config-defaults-${BUILD_TAG}.yml" \
    "A complete version of Stroom-Proxy's configuration with all its default values"

  copy_release_artefact \
    "${proxy_release_config_dir}/config-schema.yml" \
    "${RELEASE_ARTEFACTS_DIR}/stroom-proxy-app-config-schema-${BUILD_TAG}.yml" \
    "The schema for Stroom-Proxy's configuration file"

  copy_release_artefact \
    "${BUILD_DIR}/stroom-proxy/stroom-proxy-app/build/distributions/stroom-proxy-app-${BUILD_TAG}.zip" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The archive containing the Stroom-Proxy application distribution"

  # Stroom (Headless)
  copy_release_artefact \
    "${BUILD_DIR}/stroom-headless/build/distributions/stroom-headless-${BUILD_TAG}.zip" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The archive containing the Stroom-Headless application distribution"

  # Entity relationship diagram for the DB
  copy_release_artefact \
    "${BUILD_DIR}/container_build/build/entity-relationships-${BUILD_TAG}.svg" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "An entity relationship diagram for the Stroom database"

  # DB DDL SQL
  copy_release_artefact \
    "${DDL_DUMP_FILE}" \
    "${RELEASE_ARTEFACTS_DIR}" \
    "The Stroom database schema SQL"

  format_manifest_file

  # Now generate hashes for all the zips
  for file in "${RELEASE_ARTEFACTS_DIR}"/*.zip; do
    create_file_hash "${file}"
  done
  echo "::endgroup::"
}

format_manifest_file() {
  local temp_file
  temp_file="$(mktemp)"
  # Now format the manifest into columns
  # Replace ' - ' with '|' then split to columns on '|'
  sed 's/ - /|/g' "${RELEASE_MANIFEST}" \
    | column -t -s\| \
    > "${temp_file}"

  mv "${temp_file}" "${RELEASE_MANIFEST}"
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
  # If we have a BUILD_TAG (git tag) then use that, else use the floating tag
  docker build \
    "${allTagArgs[@]}" \
    --build-arg GIT_COMMIT="${BUILD_COMMIT}" \
    --build-arg GIT_TAG="${BUILD_TAG:-${SNAPSHOT_FLOATING_TAG}}" \
    "${contextRoot}"

  if [[ -z "${LOCAL_BUILD}" ]]; then
    echo -e "Pushing the docker image to ${GREEN}${dockerRepo}${NC} with" \
      "tags: ${GREEN}${allTagArgs[*]}${NC}"
    docker \
      push \
      --all-tags \
      "${dockerRepo}"
  else
    echo -e "${YELLOW}LOCAL_BUILD set so skipping docker push${NC}"
  fi

  echo -e "Completed Docker release"
}

docker_login() {
  # The username and password are configured in the travis gui
  if [[ -n "${DOCKER_USERNAME}" ]] && [[ -n "${DOCKER_PASSWORD}" ]]; then
    # Docker login stores the creds in a file so check it to
    # see if we are already logged in
    #local dockerConfigFile="${HOME}/.docker/config.json"
    #if [[ -f "${dockerConfigFile}" ]] \
      #&& grep -q "index.docker.io" "${dockerConfigFile}"; then

      #echo -e "Already logged into docker"
    #else
      echo -e "Logging in to Docker (if this fails, have you provided the" \
        "correct docker creds)"
      # Login is idempotent
      echo "${DOCKER_PASSWORD}" \
        | docker login \
          -u "${DOCKER_USERNAME}" \
          --password-stdin \
          >/dev/null 2>&1
      echo -e "Successfully logged in to docker"
    #fi
  else
    echo -e "${YELLOW}DOCKER_USERNAME and/or DOCKER_PASSWORD not set so" \
      "skipping docker login. Pulls/builds will be un-authenticated and rate" \
      "limited, pushes will fail.${NC}"
  fi
}

docker_logout() {
  # The username and password are configured in the travis gui
  if [[ -n "${DOCKER_USERNAME}" ]] && [[ -n "${DOCKER_PASSWORD}" ]]; then
    echo -e "Logging out of Docker"
    docker logout >/dev/null 2>&1
  else
    echo -e "${YELLOW}DOCKER_USERNAME and/or DOCKER_PASSWORD not set so" \
      "skipping docker logout"
  fi
}

copy_swagger_ui_content() {
  echo "::group::Copy Swagger content"
  local ghPagesDir=$BUILD_DIR/gh-pages
  local swaggerUiCloneDir=$BUILD_DIR/swagger-ui
  mkdir -p "${ghPagesDir}"
  echo "Copying swagger spec files to ${ghPagesDir}"
  # copy our generated swagger specs to gh-pages
  cp \
    "${BUILD_DIR}"/stroom-app/src/main/resources/ui/noauth/swagger/stroom.* \
    "${ghPagesDir}/"
  # clone swagger-ui repo so we can get the ui html/js/etc

  echo "Cloning swagger UI at tag ${SWAGGER_UI_GIT_TAG}"
  git clone \
    --depth 1 \
    --branch "${SWAGGER_UI_GIT_TAG}" \
    --single-branch \
    https://github.com/swagger-api/swagger-ui.git \
    "${swaggerUiCloneDir}"

  # copy the bits of swagger-ui that we need
  echo "Copying swagger UI distribution to ${ghPagesDir}"
  cp \
    -r \
    "${swaggerUiCloneDir}"/dist/* \
    "${ghPagesDir}"/

  local minor_version
  minor_version=$(echo "${BUILD_TAG}" | grep -oP "^v[0-9]+\.[0-9]+")

  # repalce the default swagger spec url in swagger UI
  # swagger is deployed to a versioned dir
  sed \
    -i \
    "s#url: \".*\"#url: \"https://gchq.github.io/stroom/${minor_version}/stroom.json\"#g" \
    "${ghPagesDir}/index.html"
  echo "::endgroup::"
}

check_for_out_of_date_puml_svgs() {

  local convert_cmd=( "./container_build/runInJavaDocker.sh" "SVG" )

  echo -e "${GREEN}Ensuring all PlantUML generated .svg files are up to date${NC}"

  # shellcheck disable=SC2068
  # Convert any .puml files into .puml.svg if the sha1 hash of the .puml file
  # does not match that in .puml.sha1 (or .puml.sha1 doesn't exist)
  ${convert_cmd[@]}

  # Now see if git thinks there are any differences
  # if so, fail the build as it means the puml has been changed
  # but the svg has not been regenerated and checked in.
  # This is to ensure that the svgs that are checked in to git
  # are up to date with their puml file.
  # This is different to stroom-docs which does not check in svg files.

  # Example git status --porcelain output:
  #  M stroom-proxy/stroom-proxy-app/doc/storing-data.puml
  #  M stroom-proxy/stroom-proxy-app/doc/storing-data.svg
  # ?? stroom-proxy/stroom-proxy-app/doc/storing-dataX.svg

  # Run the git status so we can see what git thinks has changed
  echo -e "Checking for any changes to .puml.svg files"
  # OR with true as no match on grep gives non-zero exit
  git status --porcelain \
    | grep -Po "(?<=( M|\?\?) ).*\.puml\.svg" \
    || true

  local out_of_date_file_count=0
  # grep the git status output for modified/untracked svg files and
  # grab the file path
  while read -r svg_file; do
    # Change file extension to .puml
    local puml_file="${svg_file%.svg}.puml"

    if [[ -f "${puml_file}" ]]; then
      echo -e "${RED}ERROR${NC}: PlantUML generated file ${svg_file}" \
        "is out of date${NC}"
      out_of_date_file_count=$((out_of_date_file_count + 1))
    fi
  done < <(git status --porcelain \
    | grep -Po "(?<=( M|\?\?) ).*\.puml\.svg")

  if [[ ${out_of_date_file_count} -gt 0 ]]; then
    echo -e "${RED}ERROR${NC}: ${out_of_date_file_count} PlantUML generated" \
      "file(s) are out of date. Run '${convert_cmd[*]}' to update them," \
    "then commit. Failing the build!${NC}"

    exit 1
  fi
}


# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script proper starts here
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# establish what version of stroom we are building
if [ -n "$BUILD_TAG" ]; then
  # Tagged commit so use that as our stroom version, e.g. v6.0.0
  BUILD_VERSION="${BUILD_TAG}"
else
  # No tag so use the branch name as the version, e.g. dev
  BUILD_VERSION="${BUILD_BRANCH}"
fi

# Dump all the env vars to the console for debugging
echo -e "HOME:                          [${GREEN}${HOME}${NC}]"
echo -e "BUILD_DIR:                     [${GREEN}${BUILD_DIR}${NC}]"
echo -e "BUILD_COMMIT:                  [${GREEN}${BUILD_COMMIT}${NC}]"
echo -e "BUILD_BRANCH:                  [${GREEN}${BUILD_BRANCH}${NC}]"
echo -e "BUILD_TAG:                     [${GREEN}${BUILD_TAG}${NC}]"
echo -e "BUILD_IS_PULL_REQUEST:         [${GREEN}${BUILD_IS_PULL_REQUEST}${NC}]"
echo -e "BUILD_VERSION:                 [${GREEN}${BUILD_VERSION}${NC}]"
echo -e "STROOM_RESOURCES_GIT_TAG:      [${GREEN}${STROOM_RESOURCES_GIT_TAG}${NC}]"
echo -e "LOCAL_BUILD:                   [${GREEN}${LOCAL_BUILD}${NC}]"
echo -e "docker version:                [${GREEN}$(docker --version)${NC}]"
echo -e "docker-compose version:        [${GREEN}$(docker compose version)${NC}]"
echo -e "git version:                   [${GREEN}$(git --version)${NC}]"

# Normal commit/PR/tag build
extraBuildArgs=()
allDockerTags=()

if [ "$BUILD_IS_PULL_REQUEST" = "true" ]; then
  # Pull request so no build required
  doDockerBuild=false
elif [ -n "$BUILD_TAG" ]; then
  # Tagged release so we want docker builds
  # If tag is v7.1.2 then we want the following docker tags
  # v7.1.2 (fixed tag)
  # v7.1-LATEST (floating tag)
  # v7-LATEST (floating tag)
  doDockerBuild=true

  # This is a tagged commit, so create a docker image with that tag
  VERSION_FIXED_TAG="${BUILD_TAG}"

  # Extract the major version part for a floating tag
  majorVer=$(echo "${BUILD_TAG}" | grep -oP "^v[0-9]+")
  if [ -n "${majorVer}" ]; then
    MAJOR_VER_FLOATING_TAG="${majorVer}${LATEST_SUFFIX}"
  fi

  # Extract the minor version part for a floating tag
  minorVer=$(echo "${BUILD_TAG}" | grep -oP "^v[0-9]+\.[0-9]+")
  if [ -n "${minorVer}" ]; then
    MINOR_VER_FLOATING_TAG="${minorVer}${LATEST_SUFFIX}"
  fi

  # TODO - the major and minor floating tags assume that the release
  # builds are all done in strict sequence If say the build for v6.0.1 is
  # re-run after the build for v6.0.2 has run then v6.0-LATEST will point
  # to v6.0.1 which is incorrect, hopefully this course of events is
  # unlikely to happen
  allDockerTags=( \
    "${VERSION_FIXED_TAG}" \
    "${MAJOR_VER_FLOATING_TAG}" \
    "${MINOR_VER_FLOATING_TAG}" \
  )

  if [[ "$BUILD_TAG" =~ ${RELEASE_VERSION_REGEX} ]]; then
    echo "This is a release version so add gradle arg for publishing" \
      "libs to Maven Central"
    # TODO need to add in the sonatype build args when we have decided
    # what we are publishing from stroom
    #extraBuildArgs+=("bintrayUpload")
  fi
elif [[ "$BUILD_BRANCH" =~ $BRANCH_WHITELIST_REGEX ]]; then
  # Not a tagged release but is a whitelisted branch so create a snapshot
  # docker tag, e.g. 7.0-SNAPSHOT
  # This is a branch we want to create a floating snapshot docker image for
  SNAPSHOT_FLOATING_TAG="${BUILD_BRANCH}-SNAPSHOT"
  doDockerBuild=true

  allDockerTags=( \
    "${SNAPSHOT_FLOATING_TAG}" \
  )
fi

echo -e "VERSION FIXED DOCKER TAG:      [${GREEN}${VERSION_FIXED_TAG}${NC}]"
echo -e "SNAPSHOT FLOATING DOCKER TAG:  [${GREEN}${SNAPSHOT_FLOATING_TAG}${NC}]"
echo -e "MAJOR VER FLOATING DOCKER TAG: [${GREEN}${MAJOR_VER_FLOATING_TAG}${NC}]"
echo -e "MINOR VER FLOATING DOCKER TAG: [${GREEN}${MINOR_VER_FLOATING_TAG}${NC}]"
echo -e "doDockerBuild:                 [${GREEN}${doDockerBuild}${NC}]"
echo -e "extraBuildArgs:                [${GREEN}${extraBuildArgs[*]}${NC}]"

pushd "${BUILD_DIR}" > /dev/null

# Login to docker so we have authenticated pulls that are not rate limited
docker_login

check_for_out_of_date_puml_svgs

echo "::group::Start stroom-all-dbs & scylladb"
start_databases stroom-all-dbs scylladb
echo "::endgroup::"

# Ensure we have a local.yml file as the integration tests will need it
./local.yml.sh

echo -e "${GREEN}Running all gradle builds with build version" \
  "${BLUE}${BUILD_VERSION}${NC}"

# Make this available to the gradle build which will get passed through
# into the docker containers
export BUILD_VERSION

# MAX_WORKERS env var should be set in travis/github actions settings to
# control max gradle/gwt workers
./container_build/runInJavaDocker.sh GRADLE_BUILD

# Don't do a docker build for pull requests
if [ "$doDockerBuild" = true ]; then

  echo "::group::DockerHub release"
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
  echo "::endgroup::"
fi

# If it is a tagged build copy all the files needed for the github release
# artefacts
if [ -n "$BUILD_TAG" ]; then
  copy_swagger_ui_content

  generate_ddl_dump

  generate_entity_rel_diagram

  gather_release_artefacts
fi

docker_logout

exit 0

# vim:sw=2:ts=2:et:
