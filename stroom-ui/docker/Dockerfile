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

FROM nginx:1.13.8-alpine AS base

ENV NODE_PATH=src/
RUN apk add --no-cache \
    nodejs \
    tini \
    git \
    # We need full, non-BusyBox sed
    sed \
    # jo is a JSON tool for the command line.
    # We use it to convert environment vars to JSON. We serve this in 'public'.
    --repository http://dl-3.alpinelinux.org/alpine/edge/testing/ jo
RUN npm install --global yarn
WORKDIR /root/app
# Set tini as entrypoint
ENTRYPOINT ["/sbin/tini", "--"]

# ---- Dependencies ----
FROM base AS dependencies
COPY work/package.json package.json
RUN yarn install

# ---- Build ----
FROM dependencies AS build
COPY --from=dependencies /root/app/node_modules /root/app/node_modules
COPY work/src src
COPY work/public public
RUN yarn build

# ---- Build ----
FROM base
COPY --from=build /root/app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 5000
EXPOSE 9443

VOLUME [ "/etc/nginx/certs" ]

# An explanation of the commands below:
#   Get all relevant environment variables...
#   ...remove the STROOM_UI tag...
#   ...lowercase them...
#   ...camelCase them...
#   ...turn them into json...
#   ...save to a file.
#   Do environment variable substitution for nginx.conf
#   Serve the statics.
CMD env | grep STROOM_UI | \
    sed 's/STROOM_UI_//g' | sed -e 's/\(.*=\)/\L&/' | sed -r 's/_([a-z])/\U\1/g' | \
    jo > /usr/share/nginx/html/config.json \
    && envsubst '${NGINX_HOST} \
    ${NGINX_HTTP_PORT} \
    ${NGINX_HTTPS_PORT} \
    ${NGINX_SSL_CERTIFICATE} \
    ${NGINX_SSL_CERTIFICATE_KEY} \
    ${NGINX_SSL_CLIENT_CERTIFICATE}' \
    < /etc/nginx/template/nginx.conf.template > /etc/nginx/nginx.conf \
    && nginx -g 'daemon off;'
