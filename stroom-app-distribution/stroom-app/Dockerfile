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

FROM openjdk:8-alpine

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"

#Copy the content packs in so they get imported on first run of Stroom
ADD . /usr/stroom-app
ADD https://github.com/gchq/stroom-content/releases/download/stroom-101-v1.0/stroom-101-v1.0.zip /usr/stroom-app/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/event-logging-xml-schema-v1.0/event-logging-xml-schema.zip /usr/stroom-app/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/core-xml-schemas-v1.0/core-xml-schemas.zip /usr/stroom-app/contentPackImport/
ADD https://github.com/gchq/stroom-visualisations-dev/releases/download/v3.0.4/visualisations-production-v3.0.4.zip /usr/stroom-app/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/internal-statistics-v1.2/internal-statistics-v1.2.zip /usr/stroom-app/contentPackImport/
ADD https://github.com/gchq/stroom-content/releases/download/internal-dashboards-v1.1/internal-dashboards-v1.1.zip /usr/stroom-app/contentPackImport/

WORKDIR /usr/stroom-app

# update alpine and install Bash as it is not in alpine by default
# then run setup to configure the tomcat instance
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk update && \
    apk upgrade && \
    apk add --no-cache bash && \
    rm -rf /var/cache/apk/* && \
    mkdir -p ./volumes && \
    ./bin/setup.sh

#Create Docker volume for Stroom's volumes dir
VOLUME /usr/stroom-app/volumes/

# export 8080 for stroom to listen on
EXPOSE 8080

# start Stroom and tail the log file
CMD ./bin/start.sh && mkdir -p ./instance/logs && touch ./instance/logs/stroom.log && tail -f ./instance/logs/stroom.log
