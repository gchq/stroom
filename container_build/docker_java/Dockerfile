#**********************************************************************
# Copyright 2018 Crown Copyright
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
#**********************************************************************

# This is our java build image for building stroom
# It also contains plantuml for plant uml image generation
# Using 'openjdk' on Alpine is not fully supported so using Eclipse Temurin JDK to ensure we have a known jdk version
# See https://github.com/docker-library/docs/blob/master/openjdk/README.md#openjdkversion-alpine
FROM eclipse-temurin:25_36-jdk-alpine-3.22

# Work from the shared git repo dir
WORKDIR /builder/shared

# Pass in the uid/gid of the running user so we can use the same user id
# in the container so that any files created can be read outside the 
# container.
ARG USER_ID
ARG GROUP_ID    
# The gid of the docker group on the host, needed so we can add our
# non-root user to it to get access to the docker.sock
ARG DOCKER_GROUP_ID    

# Pass in the abs path to the repo root on the docker host
# Needed to allow us to mount it in containers we spawn from here.
ARG HOST_REPO_DIR    

# The IP/hostname of the docker host so we can comminicate with other containers
# via the host's address
# The name DOCKER_HOST is used by docker so don't use that.
ARG DOCKER_HOST_IP

ARG PLANTUML_VERSION=1.2023.10

# Set the user ID into an env var so the entrypoint can see it
ENV CONTAINER_USER_ID=$USER_ID \
    HOST_REPO_DIR=$HOST_REPO_DIR \
    DOCKER_HOST_IP=$DOCKER_HOST_IP \
    LANG=en_GB.UTF-8

# Set tini as entrypoint
#ENTRYPOINT ["/sbin/tini", "--", "/builder/docker-entrypoint.sh"]

CMD id

# graphviz, ttf-* for plantuml
# rsync for gradle task :stroom-app:gwt:jakarta
# As this container will be trying to use the docker cli to run/interact with other
# containers we need to add our non-root user to the docker group i.e. the same gid as
# on the host, but may have a different name or that gid in the container or none at all.
# If we have the gid then find its name and add the user to it, if not create a group
# with that gid then add the user.
# We need this membership to have access to docker.sock.
RUN apk add --no-cache \
      bash \
      curl \
      docker-cli \
      git \
      graphviz \
      rsync \
      su-exec \
      tini \
      font-droid \
      font-droid-nonlatin \
    && curl \
      -Ls \
      https://github.com/plantuml/plantuml/releases/download/v$PLANTUML_VERSION/plantuml-$PLANTUML_VERSION.jar \
      -o /builder/plantuml.jar \
    && alias ll='ls -l' \
    && apk del curl \
    && echo "USER_ID: [$USER_ID]" \
    && echo "GROUP_ID: [$GROUP_ID]" \
    && echo "PLANTUML_VERSION: [$PLANTUML_VERSION]" \
    && echo "DOCKER_GROUP_ID: [$DOCKER_GROUP_ID]" \
    && echo \
    && echo "Ensuring group exists for group id [${GROUP_ID}]" \
    && group_name="$(cat /etc/group | grep ":${GROUP_ID}:" | awk -F ":" '{ print $1 }')" \
    && echo "group_name from /etc/group: [$group_name"] \
    && if [ -n "${group_name}" ]; then echo "Found group [${group_name}] with id ${GROUP_ID}"; fi \
    && if [ ! -n "${group_name}" ]; then echo "Creating group [builder] with id ${GROUP_ID}"; fi \
    && if [ ! -n "${group_name}" ]; then addgroup -g "$GROUP_ID" -S builder; fi \
    && if [ ! -n "${group_name}" ]; then group_name="builder"; fi \
    && echo "group_name: [$group_name"] \
    && echo \
    && echo "Ensuring user exists for user id [${USER_ID}]" \
    && user_name="$(getent passwd "$USER_ID" | cut -d: -f1)" \
    && echo "user_name from passwd with id ${USER_ID}: [$user_name]" \
    && if [ -n "${user_name}" ]; then echo "Found user [${user_name}] with id ${USER_ID}"; fi \
    && if [ ! -n "${user_name}" ]; then echo "Creating user [builder] with id ${USER_ID}"; fi \
    && if [ ! -n "${user_name}" ]; then adduser -u "$USER_ID" -S -s /bin/false -D -G "${group_name}" builder; fi \
    && if [ ! -n "${user_name}" ]; then user_name="builder"; fi \
    && echo "user_name: [$user_name]" \
    && echo \
    && echo "Ensuring group exists for docker group id [${DOCKER_GROUP_ID}]" \
    && docker_group_name="$(cat /etc/group | grep ":${DOCKER_GROUP_ID}:" | awk -F ":" '{ print $1 }')" \
    && if [ -n "${docker_group_name}" ]; then echo "Found group [${docker_group_name}] with docker group id ${DOCKER_GROUP_ID}"; fi \
    && if [ ! -n "${docker_group_name}" ]; then echo "Creating group [docker] with id ${DOCKER_GROUP_ID}"; fi \
    && if [ ! -n "${docker_group_name}" ]; then addgroup -g $DOCKER_GROUP_ID -S docker; fi \
    && if [ ! -n "${docker_group_name}" ]; then docker_group_name="docker"; fi \
    && echo "docker_group_name: $docker_group_name" \
    && echo "Adding user [builder] to docker group [${docker_group_name}]" \
    && addgroup builder "${docker_group_name}" \
    && mkdir -p /builder/shared \
    && chown -R $USER_ID:$GROUP_ID /builder

COPY --chown=$USER_ID:$GROUP_ID *.sh /builder/

USER $USER_ID
