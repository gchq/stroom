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

# Set the user ID into an env var so the entrypoint can see it
ENV CONTAINER_USER_ID=$USER_ID

# Set tini as entrypoint
ENTRYPOINT ["/sbin/tini", "--", "/builder/docker-entrypoint.sh"]

CMD id

# We need full, non-BusyBox sed
# jo is a JSON tool for generating json output on the command line.
# We use it to convert environment vars to JSON. We serve this in 'public'.
RUN apk add --no-cache \
      bash \
      su-exec \
      tini \
      sed && \
    #addgroup -g $GROUP_ID -S builder && \
    #adduser -u $USER_ID -S -s /bin/false -D -G builder builder && \
    mkdir -p /builder/shared && \
    chown -R $USER_ID:$GROUP_ID /builder

COPY --chown=$USER_ID:$GROUP_ID docker-entrypoint.sh /builder/
