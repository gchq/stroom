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

# ~~~ content pack download stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Intermediate build stage to grab all the content packs so we can
# later copy them all in one go to reduce the number of layers
FROM scratch as content-packs
WORKDIR /content-packs

# Add in all the content packs so stroom imports them on startup
ADD https://github.com/gchq/stroom-content/releases/download/core-xml-schemas-v2.0/core-xml-schemas-v2.0.zip .
ADD https://github.com/gchq/stroom-content/releases/download/event-logging-xml-schema-v3.2.3/event-logging-xml-schema-v3.2.3.zip .
ADD https://github.com/gchq/stroom-content/releases/download/internal-dashboards-v1.1/internal-dashboards-v1.1.zip .
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-sql-v2.1/internal-statistics-sql-v2.1.zip .
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-stroom-stats-v2.1/internal-statistics-stroom-stats-v2.1.zip .
ADD https://github.com/gchq/stroom-content/releases/download/stroom-101-v1.0/stroom-101-v1.0.zip .
ADD https://github.com/gchq/stroom-content/releases/download/stroom-logs-v1.0/stroom-logs-v1.0.zip .

# Add in the visualisations for import on startup
ADD https://github.com/gchq/stroom-visualisations-dev/releases/download/v3.0.5/visualisations-production-v3.0.5.zip .
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Final build stage

# We need the full JDK for the jmap binary as used by the Heap Histogram internal stats
FROM openjdk:8u171-jdk-alpine

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
ENTRYPOINT ["/sbin/tini", "--", "docker-entrypoint.sh"]

#start the app
CMD ["java", "-jar", "stroom-app-all.jar", "server", "config/config.yml"]

# https://github.com/gchq/stroom/issues/884
# JRE fails to load fonts if there are no standard fonts in the image; ttf-DejaVu is a good choice,
# see https://github.com/docker-library/openjdk/issues/73#issuecomment-207816707

# update alpine and install Bash/tini/su-exec as they are not in alpine by default
# Create a user with no home and no shell
# No bash as sh should be sufficient
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk add --no-cache \
        ttf-dejavu \
        su-exec \
        tini && \
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
    chown -R stroom /stroom

# Copy in all the content packs downloaded in the intermediate build stage
COPY --chown=stroom:stroom --from=content-packs /content-packs /stroom/contentPackImport

# Copy all the fat jars for the application and connectors
# Most likely to have changed last
COPY --chown=stroom:stroom docker-entrypoint.sh /usr/local/bin/
COPY --chown=stroom:stroom ./build/prod.yml /stroom/config/config.yml
COPY --chown=stroom:stroom ./build/plugins /stroom/plugins/
COPY --chown=stroom:stroom ./build/stroom-app-all.jar /stroom/stroom-app-all.jar

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
ENV STROOM_CONTENT_PACK_IMPORT_ENABLE="true"
# Needed to fix 'Fontconfig warning: ignoring C.UTF-8: not a valid language tag'
ENV LANG en_GB.UTF-8
