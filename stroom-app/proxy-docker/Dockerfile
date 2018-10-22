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

FROM openjdk:8u171-jre-alpine

# This is where stroom will run from and any local data will be
WORKDIR /stroom-proxy

# export 9090/9091 for stroom-proxy to listen on
EXPOSE 8090
EXPOSE 8091

# Create Docker volume to hold all the persistent state (logs, repos, content, config)
# Volumes need to be defined after the mounts have been created and populated
VOLUME /stroom-proxy/config/
VOLUME /stroom-proxy/content/
VOLUME /stroom-proxy/repo/
VOLUME /stroom-proxy/logs/

# run entrypoint script inside tini for better unix process handling, 
# see https://github.com/krallin/tini/issues/8
ENTRYPOINT ["/sbin/tini", "--", "docker-entrypoint.sh"]

# start the app
CMD ["sh", "-c", "echo \"JAVA_OPTS: [${JAVA_OPTS}]\"; java ${JAVA_OPTS} -jar stroom-app-all.jar server /stroom-proxy/config/config.yml"]

# The following line can be used as a temproary hack in dev if the build can't
# resolve alpine's apk repo server.
#RUN echo "151.101.112.249 dl-cdn.alpinelinux.org" >> /etc/hosts && \

# update alpine and install tini/su-exec as they are not in alpine by default
# Create a system user/group with no home and no shell and a uid:gid of 1000:1000
# No bash as sh should be sufficient
RUN echo "http_proxy: $http_proxy" && \
    echo "https_proxy: $https_proxy" && \
    apk add --no-cache \
        su-exec \
        tini && \
    addgroup -g 1000 -S proxy && \
    adduser -u 1000 -S -s /bin/false -D -G proxy proxy && \
    mkdir -p /stroom-proxy/config && \
    mkdir -p /stroom-proxy/content && \
    mkdir -p /stroom-proxy/logs/access && \
    mkdir -p /stroom-proxy/logs/events && \
    mkdir -p /stroom-proxy/logs/app && \
    mkdir -p /stroom-proxy/repo && \
    chown -R proxy:proxy /stroom-proxy

# Order matters for COPY/ADD operations, least likely to change go first so cache is used
# Copy all the fat jars for the application and connectors
COPY --chown=proxy:proxy docker-entrypoint.sh /usr/local/bin/
COPY --chown=proxy:proxy ./build/proxy-prod.yml /stroom-proxy/config/config.yml
COPY --chown=proxy:proxy ./build/stroom-app-all.jar /stroom-proxy/stroom-app-all.jar

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
