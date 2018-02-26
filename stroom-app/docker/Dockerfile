#**********************************************************************
# Copyright 2016 Crown Copyright
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

FROM openjdk:8u151-jre-alpine

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
ENV STROOM_CONTENT_PACK_IMPORT_ENABLE="true"

# update alpine and install Bash/tini/su-exec as they are not in alpine by default
# Create a user with no home and no shell
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk add --no-cache \
        bash \
        su-exec \
        tini && \
    adduser -s /bin/false -D stroom && \
    mkdir -p /stroom && \
    mkdir -p /stroom/volumes && \
    mkdir -p /stroom/contentPackImport 

#This is where stroom will run from and any local data will be
WORKDIR /stroom

#Copy all the fat jars for the application and connectors
ADD ./build/stroom-app-all.jar /stroom/stroom-app-all.jar
ADD ./build/stroom-kafka-client-impl_0_10_0_1-all.jar /stroom/plugins/stroom-kafka-client-impl_0_10_0_1-all.jar
ADD ./build/stroom-elastic-client-impl_5_6_4-all.jar /stroom/plugins/stroom-elastic-client-impl_5_6_4-all.jar
ADD ./build/prod.yml /stroom/config.yml
ADD docker-entrypoint.sh /usr/local/bin/

#Add in all the content packs so stroom imports them on startup
ADD https://github.com/gchq/stroom-content/releases/download/core-xml-schemas-v2.0/core-xml-schemas-v2.0.zip /stroom/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/event-logging-xml-schema-v3.1.1/event-logging-xml-schema-v3.1.1.zip /stroom/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/internal-dashboards-v1.1/internal-dashboards-v1.1.zip /stroom/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-sql-v2.0/internal-statistics-sql-v2.0.zip /stroom/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-stroom-stats-v2.0/internal-statistics-stroom-stats-v2.0.zip /stroom/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/stroom-101-v1.0/stroom-101-v1.0.zip /stroom/contentPackImport/

#Add in the visualisations for import on startup
ADD https://github.com/gchq/stroom-visualisations-dev/releases/download/v3.0.4/visualisations-production-v3.0.4.zip /stroom/contentPackImport/

#Create Docker volume for Stroom's volumes dir
VOLUME /stroom/volumes/

#Make sure all the files are owned by the stroom user
RUN chown -R stroom /stroom

# export 8080 for stroom to listen on
EXPOSE 8080

#run entrypoint script inside tini for better unix process handling, see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "docker-entrypoint.sh"]

#start the app
CMD ["java", "-jar", "stroom-app-all.jar", "server", "config.yml"]
