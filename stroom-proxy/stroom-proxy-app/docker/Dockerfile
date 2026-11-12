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

ARG APP_JAR_FILENAME="stroom-proxy-app-all.jar"

# ~~~ common base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Intermediate build stage that is common to stroom and proxy to speed up
# the build. Keep both statements the same between this and stroom-proxy
# so they can each use the same cached layer.

# The JDK (rather than JRE) is required for the diagnostic tools like
# jstat/jmap/jcmd/etc.
# Using 'openjdk' on Alpine is not fully supported so using Eclipse Temurin JDK to ensure we have a known jdk version
# See https://github.com/docker-library/docs/blob/master/openjdk/README.md#openjdkversion-alpine
FROM redhat/ubi10-minimal:10.2-1794227009@sha256:f0c7b9ae9914fcf183e7c36fc46864903c2d2caf3af327421913ae54b3d29e94 AS common-base-stage

# bash and jq are required for Kubernetes lifecycle scripts, which interact with the API
# curl is required for the docker healthcheck
# su-exec required for running stroom as not-root user
# All the packages in the add --upgrade section had CVEs at some point.
# Make sure binutils is not present as that has unfixed CVEs

RUN \
    set -eu; \
    echo "http_proxy: ${http_proxy:-}"; \
    echo "https_proxy: ${https_proxy:-}"; \
    microdnf install -y \
        bash \
        ca-certificates \
        curl \
        glibc-langpack-en \
        jq \
        openssl \
        shadow-utils \
        tzdata && \
    microdnf clean all; \
    locale -a

# Re-define the JAVA_HOME for our custom JRE
ENV JAVA_HOME=/opt/java/runtime
ENV PATH="${JAVA_HOME}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
# Needed to fix 'Fontconfig warning: ignoring C.UTF-8: not a valid language tag'
#ENV LANG=en_GB.UTF-8 LANGUAGE=en_GB:en LC_ALL=en_GB.UTF-8
ENV LANG=en_GB.UTF-8
ENV LANGUAGE=en_GB:en
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~




# ~~~ jlink base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Build the custom JRE by scanning the app jar to find out what modules are needed
FROM eclipse-temurin:25.0.5_8-jdk-ubi10-minimal@sha256:6d870f0c9a54ea090e1ed8396a4926f183b1d379934248b0d335567282417517 AS stroom_proxy_jlink_stage
# Comma delimited list of extra modules to add in
# We need jdk.jcmd so we can take heap dumps if needs be
ARG EXTRA_MODULES="jdk.jcmd"
ARG JAVA_MAJOR_VERSION="25"
ARG APP_JAR_FILENAME
COPY "./build/${APP_JAR_FILENAME}" /app/
RUN set -eux; \
    MODULES="$(jdeps -quiet --recursive --print-module-deps --ignore-missing-deps --multi-release ${JAVA_MAJOR_VERSION} "/app/${APP_JAR_FILENAME}" )"; \
    MODULES="${MODULES}${EXTRA_MODULES:+,${EXTRA_MODULES}}"; \
    echo "jlink modules: ${MODULES}"; \
    jlink \
        --add-modules "${MODULES}" \
        --no-header-files \
        --compress=zip-9 \
        --output /javaruntime
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~



# ~~~ Final build stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

FROM common-base-stage AS main-stage

ARG USER_NAME=proxy
ARG USER_ID=1000
# The root group which should be GID 0
# This is to allow OKD/Openshift to run as any user it likes that is also a member of GID 0
ARG GROUP_NAME=root
ARG GROUP_ID=0
ARG APP_JAR_FILENAME

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"

# This is where stroom will run from and any local data will be
WORKDIR /stroom-proxy

# export 9090/9091 for stroom-proxy to listen on
EXPOSE 8090
EXPOSE 8091

ENTRYPOINT ["/stroom-proxy/docker-entrypoint.sh"]

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
    set -eu; \
    if ! getent group "${GROUP_NAME}" >/dev/null 2>&1; then \
        groupadd \
            --gid "${GROUP_ID}" \
            --system "${GROUP_NAME}"; \
    fi; \
    useradd \
        --uid ${USER_ID} \
        --system \
        --gid ${GROUP_ID} \
        --shell /sbin/nologin \
        --no-create-home ${USER_NAME}; \
    # was needed for groupadd/useradd, so now can be removed \
    microdnf remove -y \
        shadow-utils; \
    mkdir -p /stroom-proxy/certs; \
    mkdir -p /stroom-proxy/config; \
    mkdir -p /stroom-proxy/config_fallback; \
    mkdir -p /stroom-proxy/content; \
    mkdir -p /stroom-proxy/data; \
    mkdir -p /stroom-proxy/data_feed_identities; \
    mkdir -p /stroom-proxy/failures; \
    mkdir -p /stroom-proxy/logs/access; \
    mkdir -p /stroom-proxy/logs/app; \
    mkdir -p /stroom-proxy/logs/receive; \
    mkdir -p /stroom-proxy/logs/send; \
    mkdir -p /stroom-proxy/zip_file_ingest; \
    mkdir -p /stroom-proxy/zip_file_ingest_failed; \
    chown -R "$USER_ID:$GROUP_ID" /stroom-proxy; \
    # Give the group the same perms as the user so any user with GID 0 can run proxy in \
    # the same way as UID 1000 \
    chmod -R 770 /stroom-proxy

# Copy all the fat jars for the application and connectors

# Order matters for COPY/ADD operations, least likely to change go first so cache is used

COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=770 \
    *.sh \
    /stroom-proxy/

# This config file is a fallback for use in development
# (or when a stack with the config bind mount is not available)
COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    build/config.yml \
    /stroom-proxy/config_fallback/config.yml

# Our jlink generateed custom java runtime environment
COPY \
    --from=stroom_proxy_jlink_stage \
    /javaruntime \
    "${JAVA_HOME}"

COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    "./build/${APP_JAR_FILENAME}" \
    /stroom-proxy/

# Label the image so we can see what commit/tag it came from
ARG GIT_COMMIT=unspecified
ARG GIT_TAG=unspecified

# Pass the GIT_TAG through to the running container
# This should not be set at container run time
ENV GIT_TAG=$GIT_TAG

LABEL \
    git_commit="$GIT_COMMIT" \
    git_tag="$GIT_TAG"

# !!! IMPORTANT !!!
# If you add a new volume, make sure the dir is created in the RUN above,
# so that it can get the right ownership/perms

# Create Docker volume to hold all the persistent state (logs, repos, content, config)
# Volumes need to be defined after the mounts have been created and populated
VOLUME /stroom-proxy/certs/
VOLUME /stroom-proxy/config/
VOLUME /stroom-proxy/content/
VOLUME /stroom-proxy/data/
VOLUME /stroom-proxy/data_feed_identities/
VOLUME /stroom-proxy/failures/
VOLUME /stroom-proxy/logs/
VOLUME /stroom-proxy/zip_file_ingest/
VOLUME /stroom-proxy/zip_file_ingest_failed/

# Run as our non-root user
USER "$USER_ID"

# vim: set tabstop=4 shiftwidth=4 expandtab:

