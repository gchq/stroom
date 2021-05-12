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

FROM adoptopenjdk/openjdk15:jdk-15.0.2_7-alpine as stroom-base-stage

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

ARG PLANTUML_VERSION=1.2021.5

# Set the user ID into an env var so the entrypoint can see it
ENV CONTAINER_USER_ID=$USER_ID

ENV HOST_REPO_DIR=$HOST_REPO_DIR
ENV DOCKER_HOST_IP=$DOCKER_HOST_IP

ENV LANG en_GB.UTF-8

# Set tini as entrypoint
#ENTRYPOINT ["/sbin/tini", "--", "/builder/docker-entrypoint.sh"]

CMD id

    #&& #sudo usermod -a -G docker builder
      #busybox-extras \

# iputils for non-root ping
# busybox-extras for telnet for debugging use
# graphviz, ttf-* for plantuml
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
      iputils \
      su-exec \
      tini \
      ttf-droid \
      ttf-droid-nonlatin \
    && curl -Ls https://sourceforge.net/projects/plantuml/files/plantuml.$PLANTUML_VERSION.jar/download -o /builder/plantuml.jar \
    && alias ll='ls -l' \
    && apk del curl \
    && echo "USER_ID: $USER_ID" \
    && echo "GROUP_ID: $GROUP_ID" \
    && echo "PLANTUML_VERSION: $PLANTUML_VERSION" \
    && echo "DOCKER_GROUP_ID: $DOCKER_GROUP_ID" \
    && docker_group_name="$(cat /etc/group | grep ":${DOCKER_GROUP_ID}:" | awk -F ":" '{ print $1 }')" \
    && if [ ! -n ${docker_group_name} ]; then addgroup -g $DOCKER_GROUP_ID -S docker; fi \
    && if [ ! -n ${docker_group_name} ]; then docker_group_name="docker"; fi \
    && echo "docker_group_name: $docker_group_name" \
    && addgroup -g $GROUP_ID -S builder \
    && adduser -u $USER_ID -S -s /bin/false -D -G builder builder \
    && addgroup builder "${docker_group_name}" \
    && mkdir -p /builder/shared \
    && chown -R $USER_ID:$GROUP_ID /builder

COPY --chown=$USER_ID:$GROUP_ID docker-entrypoint.sh /builder/

USER $USER_ID
