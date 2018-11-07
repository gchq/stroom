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

FROM openjdk:8u181-jdk-alpine3.8 as stroom-base-stage

# bash/curl required for sending logs
# su-exec required for running stroom as not-root user
# tini required for process control in the entrypoint
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk add --no-cache \
        bash \
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



# ~~~ content pack download stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Intermediate build stage to grab all the content packs so we can
# later copy them all in one go to reduce the number of layers
FROM scratch as content-packs-stage
WORKDIR /content-packs

# Add in all the content packs so stroom imports them on startup
ADD https://github.com/gchq/stroom-content/releases/download/core-xml-schemas-v2.0/core-xml-schemas-v2.0.zip .
ADD https://github.com/gchq/stroom-content/releases/download/event-logging-xml-schema-v3.2.3/event-logging-xml-schema-v3.2.3.zip .
ADD https://github.com/gchq/stroom-content/releases/download/internal-dashboards-v1.1/internal-dashboards-v1.1.zip .
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-sql-v2.1/internal-statistics-sql-v2.1.zip .
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-stroom-stats-v2.1/internal-statistics-stroom-stats-v2.1.zip .
ADD https://github.com/gchq/stroom-content/releases/download/stroom-101-v1.0/stroom-101-v1.0.zip .
ADD https://github.com/gchq/stroom-content/releases/download/stroom-logs-v1.1/stroom-logs-v1.1.zip .

# Add in the visualisations for import on startup
ADD https://github.com/gchq/stroom-visualisations-dev/releases/download/v3.0.5/visualisations-production-v3.0.5.zip .
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~



# ~~~ send logs download stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Intermediate build stage to grab all the send logs scripts
# later copy them all in one go to reduce the number of layers
# Have to use alpine rather than scratch so we can chmod
FROM alpine:3.8 as send-logs-scripts-stage
WORKDIR /scripts

ARG SEND_TO_STROOM_VERSION=send-to-stroom-v1.2.1

ADD https://raw.githubusercontent.com/gchq/stroom-clients/${SEND_TO_STROOM_VERSION}/bash/send_to_stroom.sh .
ADD https://raw.githubusercontent.com/gchq/stroom-clients/${SEND_TO_STROOM_VERSION}/bash/send_to_stroom_args.sh .
RUN chmod u+x ./*.sh
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~



# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Final build stage

# We need the full JDK for the jmap binary as used by the Heap Histogram internal stats
FROM stroom-base-stage

#This is where stroom will run from and any local data will be
WORKDIR /stroom

# export 8080/8081 for stroom to listen on
EXPOSE 8080
EXPOSE 8081

# The config.yml file is driven by the environment variable substitution so
# no need to expose it as a volume

#Create Docker volume for SLF4J output
VOLUME /stroom/logs/

#Create Docker volume for any output stroom creates, e.g. from file appenders
VOLUME /stroom/output/

#Create Docker volume for the proxy aggregation repo location
VOLUME /stroom/proxy-repo/

#Create Docker volume for Stroom's volumes dir
VOLUME /stroom/volumes/

# run entrypoint script inside tini for better unix process handling, 
# see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "/stroom/docker-entrypoint.sh"]

#start the app
CMD ["sh", "-c", "echo \"JAVA_OPTS: [${JAVA_OPTS}]\"; java ${JAVA_OPTS} -jar stroom-app-all.jar server config/config.yml"]

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
    mkdir -p /stroom/contentPackImport && \
    mkdir -p /stroom/logs/access && \
    mkdir -p /stroom/logs/app && \
    mkdir -p /stroom/logs/events && \
    mkdir -p /stroom/output && \
    mkdir -p /stroom/proxy-repo && \
    mkdir -p /stroom/volumes && \
    chown -R stroom:stroom /stroom

# Copy in all the content packs downloaded in the intermediate build stage
COPY --chown=stroom:stroom --from=content-packs-stage /content-packs /stroom/contentPackImport

# Copy in all the send logs scripts downloaded in the intermediate build stage
COPY --chown=stroom:stroom --from=send-logs-scripts-stage /scripts /stroom

# Copy all the fat jars for the application and connectors
# Most likely to have changed last
COPY --chown=stroom:stroom docker-entrypoint.sh /stroom/
COPY --chown=stroom:stroom create_crontab.sh /stroom/
COPY --chown=stroom:stroom ./build/prod.yml /stroom/config/config.yml
COPY --chown=stroom:stroom ./build/plugins /stroom/plugins/

COPY --chown=stroom:stroom --from=fat-jar-stage /stroom-app-all.jar /stroom/

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
ENV STROOM_CONTENT_PACK_IMPORT_ENABLE="true"
# Needed to fix 'Fontconfig warning: ignoring C.UTF-8: not a valid language tag'
ENV LANG en_GB.UTF-8
