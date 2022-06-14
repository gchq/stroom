#!/usr/bin/env sh

ROOT_DIR=/stroom
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
      "not exist. You may not have correctly configured the /stroom/config" \
      "volume or you are running in development."
  fi

  java_opts="${JAVA_OPTS:- -Xms50m -Xmx2g}"

  echo "Starting stroom"
  echo "Commands:    [$*]"
  echo "Config file: [${config_file}]"
  echo "JAVA_OPTS:   [${java_opts}]"

  # All args passed to this script are the command and its optional args,
  # e.g. 'server', 'migrate', 'reset_password -u joe -p pword'
  #shellcheck disable=2086
  java \
    ${java_opts} \
    -jar stroom-app-all.jar \
    "$@" \
    "${config_file}"
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
