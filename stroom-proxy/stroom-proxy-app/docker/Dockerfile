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

# ~~~ stroom base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Intermediate build stage that is common to stroom and proxy to speed up
# the build

# The JDK (rather than JRE) is required for the diagnostic tools like
# jstat/jmap/jcmd/etc.
# Using 'openjdk' on Alpine is not fully supported so using Eclipse Temurin JDK to ensure we have a known jdk version
# See https://github.com/docker-library/docs/blob/master/openjdk/README.md#openjdkversion-alpine
FROM eclipse-temurin:25.0.3_9-jdk-alpine-3.23@sha256:30d9f87d702c2c1c601ed0d31e0c88ea1ea474ee7676cda7b7a59e759181c4dd AS stroom-base-stage

# curl is required for the docker healthcheck
# su-exec required for running stroom as not-root user
# tini required for process control in the entrypoint
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk add --no-cache \
        curl \
        su-exec \
        tini
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


# ~~~ Final build stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

FROM stroom-base-stage

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
ENV USER_NAME=proxy
ENV USER_ID=1000
# The root group which should be GID 0
# This is to allow OKD/Openshift to run as any user it likes that is also a member of GID 0
ENV GROUP_NAME=root
ENV GROUP_ID=0

# This is where stroom will run from and any local data will be
WORKDIR /stroom-proxy

# export 9090/9091 for stroom-proxy to listen on
EXPOSE 8090
EXPOSE 8091

# Create Docker volume to hold all the persistent state (logs, repos, content, config)
# Volumes need to be defined after the mounts have been created and populated
VOLUME /stroom-proxy/config/
VOLUME /stroom-proxy/certs/
VOLUME /stroom-proxy/content/
VOLUME /stroom-proxy/data/
VOLUME /stroom-proxy/data_feed_identities/
VOLUME /stroom-proxy/logs/
VOLUME /stroom-proxy/failures/
VOLUME /stroom-proxy/zip_file_ingest/
VOLUME /stroom-proxy/zip_file_ingest_failed/

# run entrypoint script inside tini for better unix process handling, 
# see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "/stroom-proxy/docker-entrypoint.sh"]

# start the app, the config file will either be supplied by a bind mount volume
# or a fallback version baked into the image
CMD ["./start.sh"]

# The following line can be used as a temproary hack in dev if the build can't
# resolve alpine's apk repo server.
#RUN echo "151.101.112.249 dl-cdn.alpinelinux.org" >> /etc/hosts && \

# Create a system user/group with no home and no shell and a uid:gid of 1000:0
# Set the perms so that the group has the same perms as the user so the container
# can be run as another user that has GID 0.
# adduser does not let us do --ingroup with a GID, so have to use 'root'.

RUN \
    adduser \
      --system \
      --shell /bin/false \
      --disabled-password \
      --ingroup "$GROUP_NAME" \
      --no-create-home \
      --uid "$USER_ID" \
      "$USER_NAME" && \
    mkdir -p /stroom-proxy/certs && \
    mkdir -p /stroom-proxy/config && \
    mkdir -p /stroom-proxy/config_fallback && \
    mkdir -p /stroom-proxy/content && \
    mkdir -p /stroom-proxy/logs/access && \
    mkdir -p /stroom-proxy/logs/receive && \
    mkdir -p /stroom-proxy/logs/send && \
    mkdir -p /stroom-proxy/logs/app && \
    mkdir -p /stroom-proxy/data && \
    mkdir -p /stroom-proxy/data_feed_identities && \
    mkdir -p /stroom-proxy/failures && \
    mkdir -p /stroom-proxy/zip_file_ingest && \
    mkdir -p /stroom-proxy/zip_file_ingest_failed && \
    chown -R "$USER_ID:$GROUP_ID" /stroom-proxy
    #chmod -R g+rwX /stroom-proxy

# Order matters for COPY/ADD operations, least likely to change go first so cache is used

COPY --chown=$USER_ID:$GROUP_ID *.sh /stroom-proxy/

# This config file is a fallback for use in development
# (or when a stack with the config bind mount is not available)
COPY --chown=$USER_ID:$GROUP_ID build/config.yml /stroom-proxy/config_fallback/config.yml

COPY --chown=$USER_ID:$GROUP_ID ./build/stroom-proxy-app-all.jar /stroom-proxy/

# Give the group the same perms as the user so any user with GID 0 can run proxy in
# the same way as UID 1000
RUN chmod -R g=u /stroom-proxy

# Label the image so we can see what commit/tag it came from
ARG GIT_COMMIT=unspecified
ARG GIT_TAG=unspecified
LABEL \
    git_commit="$GIT_COMMIT" \
    git_tag="$GIT_TAG"
# Pass the GIT_TAG through to the running container
# This should not be set at container run time
ENV GIT_TAG=$GIT_TAG

# Run as our non-root user
USER "$USER_ID"
