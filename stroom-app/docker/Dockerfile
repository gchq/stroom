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
FROM adoptopenjdk/openjdk12:x86_64-alpine-jre-12.0.2_10 as stroom-base-stage

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



# ~~~ fat jar stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Intermediate build stage to copy the stroom fat jar to allow stroom 
# and proxy builds to re-use the same cached layer with the fat jar in it
FROM scratch as fat-jar-stage
COPY ./build/stroom-app-all.jar /stroom-app-all.jar
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~



# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Final build stage

FROM stroom-base-stage

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
ENV STROOM_CONTENT_PACK_IMPORT_ENABLE="true"
# Needed to fix 'Fontconfig warning: ignoring C.UTF-8: not a valid language tag'
ENV LANG en_GB.UTF-8

# This is where stroom will run from and any local data will be
WORKDIR /stroom

# export 8080/8081 for stroom to listen on
EXPOSE 8080
EXPOSE 8081

# A Bind mount volume RO to the container for config files.
VOLUME /stroom/config/

# Create Docker volume for SLF4J output
VOLUME /stroom/logs/

# Create Docker volume for any output stroom creates, e.g. from file appenders
VOLUME /stroom/output/

# Create Docker volume for the proxy aggregation repo location
VOLUME /stroom/proxy-repo/

# Create Docker volume for Stroom's volumes dir
VOLUME /stroom/volumes/

# run entrypoint script inside tini for better unix process handling, 
# see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "/stroom/docker-entrypoint.sh"]

# start the app, the config file will either be supplied by a bind mount volume
# or a fallback version baked into the image
CMD ["./start.sh"]

# https://github.com/gchq/stroom/issues/884
# JRE fails to load fonts if there are no standard fonts in the image; ttf-DejaVu is a good choice,
# see https://github.com/docker-library/openjdk/issues/73#issuecomment-207816707

# Create a user with no home and no shell
RUN \
    apk add --no-cache \
        ttf-dejavu && \
    addgroup -g 1000 -S stroom && \
    adduser -u 1000 -S -s /bin/false -D -G stroom stroom && \
    mkdir -p /stroom && \
    mkdir -p /stroom/config && \
    mkdir -p /stroom/config-fallback && \
    mkdir -p /stroom/contentPackImport && \
    mkdir -p /stroom/logs/access && \
    mkdir -p /stroom/logs/app && \
    mkdir -p /stroom/logs/user && \
    mkdir -p /stroom/output && \
    mkdir -p /stroom/proxy-repo && \
    mkdir -p /stroom/volumes && \
    chown -R stroom:stroom /stroom

# Those that are most likely to have changed get copied last to speed up build

# Copy in all the content packs downloaded in the intermediate build stage
COPY --chown=stroom:stroom ./build/contentPacks /stroom/contentPackImport

# Copy all the fat jars for the application and connectors
COPY --chown=stroom:stroom *.sh /stroom/

# This config file is a fallback for use in development 
# (or when a stack with the config bind mount is not available)
COPY --chown=stroom:stroom build/prod.yml /stroom/config-fallback/config.yml

COPY --chown=stroom:stroom --from=fat-jar-stage /stroom-app-all.jar /stroom/

# Label the image so we can see what commit/tag it came from
ARG GIT_COMMIT=unspecified
ARG GIT_TAG=unspecified
LABEL \
    git_commit="$GIT_COMMIT" \
    git_tag="$GIT_TAG"
# Pass the GIT_TAG through to the running container
# This should not be set at container run time
ENV GIT_TAG=$GIT_TAG
