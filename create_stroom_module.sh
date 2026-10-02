#!/bin/bash

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

##################################################################
#  Script to create one or more sub-module directory structures  #
##################################################################

set -e

setup_echo_colours() {
  # Exit the script on any error
  set -e

  # shellcheck disable=SC2034
  if [ "${MONOCHROME}" = true ]; then
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BLUE2=''
    DGREY=''
    NC='' # No Colour
  else
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    BLUE2='\033[1;34m'
    DGREY='\e[90m'
    NC='\033[0m' # No Colour
  fi
}

error() {
  echo "${RED}ERROR${NC}: $*" >&2
  echo
}

error_exit() {
  error "$@"
  exit 1
}

determine_repo_root() {
  if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
    error_exit "You are not in a git repository."
  fi
  repo_root_dir="$(git rev-parse --show-toplevel)"
}

add_logback_test_config() {

  local logback_config_file="${sub_module_dir}/src/test/resources/logback-test.xml"
  if [ ! -f "${logback_config_file}" ]; then
    echo -e "  Creating logback test config ${logback_config_file}"

# No indenting to support heredoc
cat << EOF > "${logback_config_file}"
<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <!-- encoders are assigned the type
             ch.qos.logback.classic.encoder.PatternLayoutEncoder by default -->
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="stroom" level="debug" />

    <root level="error">
        <appender-ref ref="STDOUT" />
    </root>

</configuration>
EOF

  fi
}

add_gradle_settings_entry() {
  local settings_line="include '${module_name}:${sub_module_name}'"
  local settings_file="${repo_root_dir}/settings.gradle"

  echo -e "  Checking for presence of \"${settings_line}\" in ${settings_file}"

  #grep -F "${settings_line}" "${settings_file}"

  if grep -qF "${settings_line}" "${settings_file}"; then
    echo -e "    Already exists"
  else
    echo -e "  Adding \"${settings_line}\" to the end of ${settings_file}"
    echo "${settings_line}" >> "${settings_file}"
  fi
}

main() {
  setup_echo_colours

  if [ "$#" -lt 2 ]; then
    echo -e "${RED}Error${NC}: Invalid arguments"
    echo -e "Usage: ${BLUE}create_stroom_module.sh module_name suffix1 [suffix2 ... suffixN]${NC}"
    echo -e "e.g: ${BLUE}create_stroom_module.sh stroom-processor api impl impl-db${NC}"
    exit 1
  fi

  local module_name="$1"; shift
  # All remaining args are the required suffixes
  local suffixes=( "$@" )
  # replace all - with .

  local repo_root_dir=""
  determine_repo_root

  local module_dir="${repo_root_dir}/${module_name}"
  mkdir -p "${module_dir}"

  for suffix in "${suffixes[@]}"; do
    local sub_module_name="${module_name}-${suffix}"
    local sub_module_dir="${module_dir}/${sub_module_name}"

    if [ -d "${sub_module_dir}" ]; then
      echo -e "Sub-module ${sub_module_name} already exists"
    fi

    echo
    echo -e "Building sub-module ${sub_module_name}"

    echo -e "  Creating directories"
    #shellcheck disable=SC2086
    mkdir -p ${sub_module_dir}/src/{main,test}/{java,resources}

    echo -e "  Creating .gitkeep files"
    #shellcheck disable=SC2086
    touch ${sub_module_dir}/src/{main,test}/{java,resources}/.gitkeep

    local readme_file="${module_dir}/README.md"
    if [ ! -f "${readme_file}" ] ;then
      echo -e "  Creating module README"
      echo -e "# ${module_name}" > "${readme_file}"
    else
      echo -e "  README file already exists"
    fi

    # Replace - with .
    local gradle_module_name="${sub_module_name//-/.}"
    local gradle_build_file="${sub_module_dir}/build.gradle"

    if [ ! -f "${gradle_build_file}" ]; then
      echo -e "  Creating gradle build file ${gradle_build_file} for module ${gradle_module_name}"

      {
        echo -e "ext.moduleName = '${gradle_module_name}'"
        echo -e ""
        echo -e "dependencies {"
        echo -e ""
        echo -e "}"
      } > "${gradle_build_file}"
    else
      echo "  Gradle build file ${gradle_build_file} already exists"
    fi

    add_gradle_settings_entry

    add_logback_test_config

  done

  echo -e
  echo -e "Done"
}


main "$@"
