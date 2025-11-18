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
FROM eclipse-temurin:21.0.8_9-jdk-alpine AS stroom-base-stage

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
VOLUME /stroom-proxy/logs/
VOLUME /stroom-proxy/failures/
# sqlite_library mounted as tmpfs not a managed volumne

# run entrypoint script inside tini for better unix process handling, 
# see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "/stroom-proxy/docker-entrypoint.sh"]

# start the app, the config file will either be supplied by a bind mount volume
# or a fallback version baked into the image
CMD ["./start.sh"]

# The following line can be used as a temproary hack in dev if the build can't
# resolve alpine's apk repo server.
#RUN echo "151.101.112.249 dl-cdn.alpinelinux.org" >> /etc/hosts && \

# Create a system user/group with no home and no shell and a uid:gid of 1000:1000

# IMPORTANT!!!
# If you add any new dirs here, make sure you also chown them in docker-entrypoint.sh
# IMPORTANT!!!
RUN \
    addgroup -g 1000 -S proxy && \
    adduser -u 1000 -S -s /bin/false -D -G proxy proxy && \
    mkdir -p /stroom-proxy && \
    mkdir -p /stroom-proxy/certs && \
    mkdir -p /stroom-proxy/config && \
    mkdir -p /stroom-proxy/config_fallback && \
    mkdir -p /stroom-proxy/content && \
    mkdir -p /stroom-proxy/logs/access && \
    mkdir -p /stroom-proxy/logs/receive && \
    mkdir -p /stroom-proxy/logs/send && \
    mkdir -p /stroom-proxy/logs/app && \
    mkdir -p /stroom-proxy/data && \
    mkdir -p /stroom-proxy/failures && \
    chown -R proxy:proxy /stroom-proxy

# Copy all the fat jars for the application and connectors

# Order matters for COPY/ADD operations, least likely to change go first so cache is used
COPY --chown=proxy:proxy *.sh /stroom-proxy/

# This config file is a fallback for use in development
# (or when a stack with the config bind mount is not available)
COPY --chown=proxy:proxy build/config.yml /stroom-proxy/config_fallback/config.yml

COPY --chown=proxy:proxy ./build/stroom-proxy-app-all.jar /stroom-proxy/

# Label the image so we can see what commit/tag it came from
ARG GIT_COMMIT=unspecified
ARG GIT_TAG=unspecified
LABEL \
    git_commit="$GIT_COMMIT" \
    git_tag="$GIT_TAG"
# Pass the GIT_TAG through to the running container
# This should not be set at container run time
ENV GIT_TAG=$GIT_TAG
