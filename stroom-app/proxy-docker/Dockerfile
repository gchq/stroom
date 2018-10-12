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

FROM openjdk:8u171-jre-alpine

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"

#Create Docker volume for Stroom's volumes dir
#VOLUME /stroom-proxy/config/
#VOLUME /stroom-proxy/logs/
#VOLUME /stroom-proxy/repo/
VOLUME /stroom-proxy/state/

# update alpine and install Bash/tini/su-exec as they are not in alpine by default
# Create a user with no home and no shell
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk add --no-cache \
        bash \
        su-exec \
        tini && \
    adduser -s /bin/false -D proxy && \
    mkdir -p /stroom-proxy && \
    mkdir -p /stroom-proxy/state && \
    mkdir -p /stroom-proxy/state/content && \
    mkdir -p /stroom-proxy/state/logs && \
    mkdir -p /stroom-proxy/state/repo

#This is where stroom will run from and any local data will be
WORKDIR /stroom-proxy

#Copy all the fat jars for the application and connectors
ADD ./build/stroom-app-all.jar /stroom-proxy/stroom-app-all.jar
ADD ./build/proxy-prod.yml /stroom-proxy/state/config.yml
ADD docker-entrypoint.sh /usr/local/bin/


#Make sure all the files are owned by the proxy user
RUN chown -R proxy /stroom-proxy

# export 9090/9091 for stroom-proxy to listen on
EXPOSE 9090
EXPOSE 9091

#run entrypoint script inside tini for better unix process handling, see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "docker-entrypoint.sh"]

#start the app
CMD ["java", "-jar", "stroom-app-all.jar", "server", "/stroom-proxy/state/config.yml"]
