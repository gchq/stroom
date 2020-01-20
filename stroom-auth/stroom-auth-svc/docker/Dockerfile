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


# ~~~ Final build stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


# NOTE: 
# This Dockerfile used to be openjdk:10.0.2-jre-slim (debian)
# When this needs to be uplifted to java 10+ again the j10 version can be found at:
# commit 388db68fb90b623cc9ed360d6a20dab77134e152, tag v1.0-beta.7
# Further to this, master branch is J10 and thus openjdk:10.0.2-jre-slim 

# The JDK (rather than JRE) is required for the diagnostic tools like
# jstat/jmap/jcmd/etc.
FROM openjdk:8u212-jdk-alpine3.9 as stroom-base-stage

WORKDIR /stroom-auth-service
# RO bind mount for dropwizard yaml config
VOLUME [ "/stroom-auth-service/config" ]

# Volume for all application/access/user logs
VOLUME [ "/stroom-auth-service/logs" ]

# run entrypoint script inside tini for better unix process handling, 
# see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "/stroom-auth-service/docker-entrypoint.sh"]

# start the app, the config file will either be supplied by a bind mount volume
# or a fallback version baked into the image
CMD ["./start.sh"]

# curl is required for the docker healthcheck
# su-exec required for running stroom as not-root user
# tini required for process control in the entrypoint
# add auth:auth user:group. Use id 1000 (for want of an id to use) as this is
# more likely to match the uid for a dev on the host. Not critical but may be
# beneficial in development.
# Ensure we have all the dirs we need
# Chown everything to the auth user
RUN apk add --no-cache \
        curl \
        su-exec \
        tini && \
    addgroup -g 1000 -S auth && \
    adduser -u 1000 -S -s /bin/false -D -G auth auth && \
    mkdir -p /stroom-auth-service && \
    mkdir -p /stroom-auth-service/config && \
    mkdir -p /stroom-auth-service/config-fallback && \
    mkdir -p /stroom-auth-service/logs/access && \
    mkdir -p /stroom-auth-service/logs/user && \
    mkdir -p /stroom-auth-service/logs/app && \
    chown -R auth:auth /stroom-auth-service

# Copy all the artifacts required to run stroom-auth-service
# In order of likelyhood to change, most staic first.

COPY --chown=auth:auth ./*.sh /stroom-auth-service/

# This config file is a fallback for use in development 
# (or when a stack with the config bind mount is not available)
COPY --chown=auth:auth ./build/config.yml /stroom-auth-service/config-fallback/config.yml

COPY --chown=auth:auth ./build/stroom-auth-service-all.jar /stroom-auth-service/

# Label the image so we can see what commit/tag it came from
ARG GIT_COMMIT=unspecified
ARG GIT_TAG=unspecified
LABEL \
    git_commit="$GIT_COMMIT" \
    git_tag="$GIT_TAG"
# Pass the GIT_TAG through to the running container
# This should not be set at container run time
ENV GIT_TAG=$GIT_TAG
