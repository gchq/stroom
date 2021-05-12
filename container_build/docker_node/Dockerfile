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

FROM node:15.14.0-alpine3.13
WORKDIR /builder

# Pass in the uid/gid of the running user so we can use the same user id
# in the container so that any files created can be read outside the 
# container.
ARG USER_ID
ARG GROUP_ID    

# Pass in the abs path to the repo root on the docker host
ARG HOST_REPO_DIR    

# Set the user ID into an env var so the entrypoint can see it
ENV CONTAINER_USER_ID=$USER_ID

# Set tini as entrypoint
ENTRYPOINT ["/sbin/tini", "--", "/builder/docker-entrypoint.sh"]

CMD id

# We don't know if the uid/gid on the host are in used in the container
# or not so we need to test for it and handle accordingly
RUN apk add --no-cache \
      bash \
      git \
      su-exec \
      tini \
    && echo "USER_ID: [$USER_ID]" \
    && echo "GROUP_ID: [$GROUP_ID]" \
    && group_name="$(cat /etc/group | grep ":${GROUP_ID}:" | awk -F ":" '{ print $1 }')" \
    && echo "group_name: [$group_name"] \
    && if [ ! -n ${group_name} ]; then addgroup -g $GROUP_ID -S builder; fi \
    && echo "group_name: [$group_name"] \
    && if [ ! -n ${group_name} ]; then docker_group_name="builder"; fi \
    && echo "group_name: [$group_name"] \
    && user_name="$(getent passwd "$USER_ID" | cut -d: -f1)" \
    && echo "user_name: [$user_name]" \
    && if [ ! -n ${user_name} ]; then adduser -u $USER_ID -S -s /bin/false -D -G ${group_name} builder; fi \
    && echo "user_name: [$user_name]" \
    && if [ ! -n ${user_name} ]; then user_name="builder"; fi \
    && echo "user_name: [$user_name]" \
    && mkdir -p /builder/shared \
    && chown -R $USER_ID:$GROUP_ID /builder

COPY --chown=$USER_ID:$GROUP_ID docker-entrypoint.sh /builder/
