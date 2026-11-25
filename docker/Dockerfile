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


#**********************************************************************
# This is the combined dockerfile for both stroom and stroom-proxy.
# When building, you must use the '--target' arg to specifiy which terminal
# build stage you want to build, stroom_final_stage or stroom_proxy_final_stage.
#**********************************************************************



# Should match jdk base stage image
ARG JAVA_MAJOR_VERSION="25"

# ~~~~~~~~~~~~~~~~~~~~
# Stroom specific args
# ~~~~~~~~~~~~~~~~~~~~
ARG STROOM_APP_JAR_FILENAME="stroom-app-all.jar"
ARG STROOM_CONTEXT_ROOT_DIR="stroom"
# Comma delimited list of extra modules to add in
# We need jdk.jcmd so we can take heap dumps using jmap, jcmd, etc.
# We need jdk.jfr because HeapHistogramService calls a JMX MBean for heap histo stats
ARG STROOM_EXTRA_MODULES="jdk.jcmd,jdk.jfr"

# ~~~~~~~~~~~~~~~~~~~~~~~~~~
# Stroom-proxy specific args
# ~~~~~~~~~~~~~~~~~~~~~~~~~~
ARG STROOM_PROXY_APP_JAR_FILENAME="stroom-proxy-app-all.jar"
ARG STROOM_PROXY_CONTEXT_ROOT_DIR="stroom-proxy"
# Comma delimited list of extra modules to add in
# We need jdk.jcmd so we can take heap dumps using jmap, jcmd, etc.
ARG STROOM_PROXY_EXTRA_MODULES="jdk.jcmd"


# The base image for our final stages
ARG UBI_IMAGE_TAG="redhat/ubi10-minimal:10.2-1794227009@sha256:f0c7b9ae9914fcf183e7c36fc46864903c2d2caf3af327421913ae54b3d29e94"




# ~~~ jdk base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Used only for building our two custom JREs
# IMPORTANT - If you change this, copy the tag+sha into container_build/docker_java/Dockerfile
# so they can both use the same base.
FROM eclipse-temurin:25.0.5.1_2-jdk-ubi10-minimal@sha256:d9f1eda186c16a82fb75fb6c6c5777b4378ee3da7dca9518ac97f144de0e8c39 AS jdk_base_stage
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~





# ~~~ common base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Intermediate build stage that is common to stroom and proxy to speed up
# the build. Only add binaries common to both.
FROM redhat/ubi10-minimal:10.3-1795196920@sha256:d63890a635f8f19177bce1775f74f587a47f4456844f1392456c4e3673a48753 AS common_base_stage

# bash, jq, openssl and curl are required for Kubernetes lifecycle scripts, which interact with the API.
# curl is also required for the docker compose healthcheck
# util-linux needed for setpriv in entrypoint
RUN \
    set -eu; \
    echo "http_proxy: ${http_proxy:-}"; \
    echo "https_proxy: ${https_proxy:-}"; \
    microdnf install -y --nodocs \
        bash \
        ca-certificates \
        curl \
        glibc-langpack-en \
        jq \
        openssl \
        util-linux; \
    microdnf clean all

# Re-define the JAVA_HOME for our custom JRE
ENV JAVA_HOME=/opt/java/runtime
ENV PATH="${JAVA_HOME}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
# Needed to fix 'Fontconfig warning: ignoring C.UTF-8: not a valid language tag'
#ENV LANG=en_GB.UTF-8 LANGUAGE=en_GB:en LC_ALL=en_GB.UTF-8
ENV LANG=en_GB.UTF-8
ENV LANGUAGE=en_GB:en
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~





# ~~~ stroom jlink base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Build the custom JRE by scanning the app jar to find out what modules are needed
FROM jdk_base_stage AS stroom_jlink_stage
ARG JAVA_MAJOR_VERSION
ARG STROOM_EXTRA_MODULES
ARG EXTRA_MODULES="${STROOM_EXTRA_MODULES}"
ARG STROOM_APP_JAR_FILENAME
COPY "./stroom/build/${STROOM_APP_JAR_FILENAME}" /app/
RUN set -eux; \
    # Examine the jar to work out what modules we need \
    MODULES="$( \
        jdeps \
        -quiet \
        --recursive \
        --print-module-deps \
        --ignore-missing-deps \
        --multi-release \
        ${JAVA_MAJOR_VERSION} \
        "/app/${STROOM_APP_JAR_FILENAME}" )"; \
    MODULES="${MODULES}${EXTRA_MODULES:+,${EXTRA_MODULES}}"; \
    echo "jlink modules: ${MODULES}"; \
    # Build the custom JRE \
    jlink \
        --add-modules "${MODULES}" \
        --no-header-files \
        --compress=zip-9 \
        --output /javaruntime
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~






# ~~~ stroom-proxy jlink base stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Build the custom JRE by scanning the app jar to find out what modules are needed
FROM jdk_base_stage AS stroom_proxy_jlink_stage
ARG JAVA_MAJOR_VERSION
ARG STROOM_PROXY_EXTRA_MODULES
ARG EXTRA_MODULES="${STROOM_PROXY_EXTRA_MODULES}"
ARG STROOM_PROXY_APP_JAR_FILENAME
COPY "./stroom-proxy/build/${STROOM_PROXY_APP_JAR_FILENAME}" /app/
RUN set -eux; \
    # Examine the jar to work out what modules we need \
    MODULES="$( \
        jdeps \
        -quiet \
        --recursive \
        --print-module-deps \
        --ignore-missing-deps \
        --multi-release \
        ${JAVA_MAJOR_VERSION} \
        "/app/${STROOM_PROXY_APP_JAR_FILENAME}" )"; \
    MODULES="${MODULES}${EXTRA_MODULES:+,${EXTRA_MODULES}}"; \
    echo "jlink modules: ${MODULES}"; \
    # Build the custom JRE \
    jlink \
        --add-modules "${MODULES}" \
        --no-header-files \
        --compress=zip-9 \
        --output /javaruntime
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~






# ~~~ Stroom final build stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
FROM common_base_stage AS stroom_final_stage

ARG USER_NAME=stroom
ARG USER_ID=1000
# The root group which should be GID 0
# This is to allow OKD/Openshift to run as any user it likes that is also a member of GID 0
ARG GROUP_NAME=root
ARG GROUP_ID=0
ARG STROOM_APP_JAR_FILENAME

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"
ENV STROOM_CONTENT_PACK_IMPORT_ENABLE="true"
# Set the user ID into env so entrypoint can see it
ENV USER_ID=$USER_ID
ENV GROUP_ID=$GROUP_ID
# Setting this in case java needs it
ENV HOME=/stroom

# This is where stroom will run from and any local data will be
WORKDIR /stroom

# export 8080/8081 for stroom to listen on
EXPOSE 8080
EXPOSE 8081

# Entrypoint must use 'exec' to ensure CMD runs as PID1 for SIGTERM handling
ENTRYPOINT [ "/stroom/docker-entrypoint.sh"]

# start the app, the config file will either be supplied by a bind mount volume
# or a fallback version baked into the image
CMD [ \
  "./start.sh", \
  "server"]

# https://github.com/gchq/stroom/issues/884
# JRE fails to load fonts if there are no standard fonts in the image; ttf-DejaVu is a good choice,
# see https://github.com/docker-library/openjdk/issues/73#issuecomment-207816707
# Fonts needed for ExcelTarget class (exporting search results to excel)

# Create a user with no home and no shell

RUN \
    set -eu; \
    # Snapshot all current installed packages so we can remove any new ones later \
    rpm -qa --queryformat "%{NAME}\n" | sort > /tmp/current_pkgs; \
    # Needed for groupadd/useradd \
    microdnf install -y --nodocs \
        shadow-utils; \
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
    # shadow-utils was only needed for groupadd/useradd, so now can be removed \
    # along with any deps it pulled in \
    rpm -qa --queryformat "%{NAME}\n" | sort > /tmp/new_pkgs; \
    # Get a list of all added packages then remove them if there are any
    added_pkgs="$(comm -13 /tmp/current_pkgs /tmp/new_pkgs)"; \
    rm /tmp/current_pkgs /tmp/new_pkgs; \
    echo "[${added_pkgs}]"; \
    [[ -n "${added_pkgs}" ]] \
        && microdnf remove -y "${added_pkgs}" ; \
    # Font pkgs needed for ExcelTarget \
    microdnf install -y --nodocs \
        fontconfig \
        dejavu-sans-fonts; \
    # Tidy up any microdnf caches \
    microdnf clean all; \
    mkdir -p /stroom/config \
        /stroom/config_fallback \
        /stroom/content_pack_import \
        /stroom/content_index \
        /stroom/data_feed_identities \
        /stroom/git_repo \
        /stroom/lmdb \
        /stroom/lmdb_library \
        /stroom/logs/access \
        /stroom/logs/app \
        /stroom/logs/migration \
        /stroom/logs/user \
        /stroom/output \
        /stroom/planb \
        /stroom/reference_data \
        /stroom/reference_staging_data \
        /stroom/search_results \
        /stroom/volumes; \
    chown -R "$USER_ID:$GROUP_ID"  /stroom; \
    # Give the group the same perms as the user so any user with GID 0 can run stroom in \
    # the same way as UID 1000 \
    chmod -R 770 /stroom

# IMPORTANT!!!
# If you add any new dirs above, make sure you also chown them in docker-entrypoint.sh
# IMPORTANT!!!

# Those that are most likely to have changed get copied last to speed up build

# Copy in the content packs downloaded by gradle
COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    ./stroom/build/content_packs \
    /stroom/content_pack_import

# Copy all the distribution scripts
COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=770 \
    ./stroom/*.sh \
    /stroom/

# This config file is a fallback for use in development
# (or when a stack with the config bind mount is not available)
COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    ./stroom/build/config.yml \
    /stroom/config_fallback/config.yml

# Our jlink generateed custom java runtime environment
# No chmod needed, they go in as
# -rwxr-xr-x 1 root root
COPY \
    --from=stroom_jlink_stage \
    /javaruntime \
    ${JAVA_HOME}

# The application fat jar
COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    "./stroom/build/${STROOM_APP_JAR_FILENAME}" \
    /stroom/

# Intentiaonally done near the end because they will often change on each build
ARG GIT_COMMIT=unspecified
ARG GIT_TAG=unspecified

# Pass the GIT_TAG through to the running container
# This should not be set at container run time
ENV GIT_TAG=$GIT_TAG

# Label the image so we can see what commit/tag it came from
LABEL \
    git_commit="$GIT_COMMIT" \
    git_tag="$GIT_TAG"

# !!! IMPORTANT !!!
# If you add a new volume, make sure the dir is created in the RUN above,
# so that it can get the right ownership/perms

# A Bind mount volume RO to the container for config files.
VOLUME /stroom/config/

# Create Docker volume for indexed doc content.
VOLUME /stroom/content_index/

# Create Docker volume for the content_pack_import dir as this will be mutated on
VOLUME /stroom/content_pack_import/

# Create Docker volume for data feed idenity files to be dropped into
VOLUME /stroom/data_feed_identities/

# Create Docker volume for local checkouts of git repos
VOLUME /stroom/git_repo/

# Create Docker volume for use by any lmdb data stores.
VOLUME /stroom/lmdb/

# lmdb binaries that are extracted from the stroom jar on each boot.
# This will be mounted on tmpfs so will appear owned by root:root
VOLUME /stroom/lmdb_library/

# Create Docker volume for SLF4J output
VOLUME /stroom/logs/

# Create Docker volume for any output stroom creates, e.g. from file appenders
VOLUME /stroom/output/

# Create Docker volume for PlanB state store
VOLUME /stroom/planb/

# Create Docker volume for Stroom's off-heap reference data
VOLUME /stroom/reference_data/

# Create Docker volume for Stroom's off-heap reference data staging area
# This could potentiall be mounted as tmpfs (if the host has sufficient memory) as the
# data is only present for the life of a ref load.
VOLUME /stroom/reference_staging_data/

# Create Docker volume for Stroom's off-heap search result data
VOLUME /stroom/search_results/

# Create Docker volume for Stroom's volumes dir
VOLUME /stroom/volumes/

# Run as our non-root user
USER "$USER_ID"
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~







# ~~~ Stroom-Proxy final build stage ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
FROM common_base_stage AS stroom_proxy_final_stage

ARG USER_NAME=proxy
ARG USER_ID=1000
# The root group which should be GID 0
# This is to allow OKD/Openshift to run as any user it likes that is also a member of GID 0
ARG GROUP_NAME=root
ARG GROUP_ID=0
ARG STROOM_PROXY_APP_JAR_FILENAME

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
    # Snapshot all current installed packages so we can remove any new ones later \
    rpm -qa --queryformat "%{NAME}\n" | sort > /tmp/current_pkgs; \
    # Needed for groupadd/useradd \
    microdnf install -y --nodocs \
        shadow-utils; \
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
    # shadow-utils was only needed for groupadd/useradd, so now can be removed \
    # along with any deps it pulled in \
    rpm -qa --queryformat "%{NAME}\n" | sort > /tmp/new_pkgs; \
    # Get a list of all added packages then remove them if there are any
    added_pkgs="$(comm -13 /tmp/current_pkgs /tmp/new_pkgs)"; \
    rm /tmp/current_pkgs /tmp/new_pkgs; \
    echo "[${added_pkgs}]"; \
    [[ -n "${added_pkgs}" ]] \
        && microdnf remove -y "${added_pkgs}" ; \
    # Tidy up any microdnf caches \
    microdnf clean all; \
    mkdir -p /stroom-proxy/certs \
        /stroom-proxy/config \
        /stroom-proxy/config_fallback \
        /stroom-proxy/content \
        /stroom-proxy/data \
        /stroom-proxy/data_feed_identities \
        /stroom-proxy/failures \
        /stroom-proxy/logs/access \
        /stroom-proxy/logs/app \
        /stroom-proxy/logs/receive \
        /stroom-proxy/logs/send \
        /stroom-proxy/zip_file_ingest \
        /stroom-proxy/zip_file_ingest_failed; \
    chown -R "$USER_ID:$GROUP_ID" /stroom-proxy; \
    # Give the group the same perms as the user so any user with GID 0 can run proxy in \
    # the same way as UID 1000 \
    chmod -R 770 /stroom-proxy

# Copy all the fat jars for the application and connectors

# Order matters for COPY/ADD operations, least likely to change go first so cache is used

COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=770 \
    ./stroom-proxy/*.sh \
    /stroom-proxy/

# This config file is a fallback for use in development
# (or when a stack with the config bind mount is not available)
COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    ./stroom-proxy/build/config.yml \
    /stroom-proxy/config_fallback/config.yml

# Our jlink generateed custom java runtime environment
# No chmod needed, they go in as
# -rwxr-xr-x 1 root root
COPY \
    --from=stroom_proxy_jlink_stage \
    /javaruntime \
    "${JAVA_HOME}"

COPY \
    --chown=$USER_ID:$GROUP_ID \
    --chmod=660 \
    "./stroom-proxy/build/${STROOM_PROXY_APP_JAR_FILENAME}" \
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
