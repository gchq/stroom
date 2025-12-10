#!/usr/bin/env sh

#
# Copyright 2016-2025 Crown Copyright
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
#

ROOT_DIR=/stroom-proxy
BIND_MOUNT_CONFIG_FILE="${ROOT_DIR}/config/config.yml"
FALLBACK_CONFIG_FILE="${ROOT_DIR}/config_fallback/config.yml"

main() {
  # To allow us to run the container outside of a stack it needs a config file
  # to work with. We bake one into the image so that if the config volume
  # is not bind mounted we can fallback on the default one.
  if [ -f "${BIND_MOUNT_CONFIG_FILE}" ]; then
    config_file="${BIND_MOUNT_CONFIG_FILE}"
  else
    config_file="${FALLBACK_CONFIG_FILE}"
    echo "WARN   Using fallback config file as ${BIND_MOUNT_CONFIG_FILE} does" \
      "not exist. You may not have correctly configured the /stroom-proxy/config" \
      "volume or you are running in development."
  fi

  local java_opts="${JAVA_OPTS:- -Xms50m -Xmx2g}"

  # Open some packages to the classpath.
  java_opts="${java_opts} --add-opens java.base/java.nio=ALL-UNNAMED"
  java_opts="${java_opts} --add-opens java.base/sun.nio.ch=ALL-UNNAMED"
  java_opts="${java_opts} --add-opens java.base/java.lang=ALL-UNNAMED"

  echo "Starting stroom-proxy"
  echo "Config file: [${config_file}]"
  echo "JAVA_OPTS:   [${java_opts}]"

  #shellcheck disable=2086
  java \
    ${java_opts} \
    -jar stroom-proxy-app-all.jar \
    server \
    "${config_file}"
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
