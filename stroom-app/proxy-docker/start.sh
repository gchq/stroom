#!/usr/bin/env sh

ROOT_DIR=/stroom-proxy
BIND_MOUNT_CONFIG_FILE="${ROOT_DIR}/config/config.yml"
FALLBACK_CONFIG_FILE="${ROOT_DIR}/config-fallback/config.yml"

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

  echo "Starting stroom-proxy"
  echo "Config file: [${config_file}]"
  echo "JAVA_OPTS:   [${JAVA_OPTS}]"

  #shellcheck disable=2086
  java ${JAVA_OPTS} -jar stroom-app-all.jar server "${config_file}"
}

main "$@"

#vim:set et sw=2 ts=2:

