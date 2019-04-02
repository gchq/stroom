#!/bin/bash
set -e

error() {
  echo "ERROR: $*" >&2
  echo
}

error_exit() {
  error "$@"
  exit 1
}

determine_repo_root() {
  if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
    error_exit "You are not in a git repository. This script should be run from" \
      "the root of a repository."
  fi
  repo_root_dir="$(git rev-parse --show-toplevel)"
}

main() {

  if [ "$#" -lt 2 ]; then
    echo "Error: Invalid arguments"
    echo "Usage: create_stroom_module.sh module_name suffix1 [suffix2 ... suffixN]"
    echo "e.g: create_stroom_module.sh stroom-processor api impl impl-db"
    echo "Run from the root of the stroom repo"
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

  local settings_content=""
  
  for suffix in "${suffixes[@]}"; do
    local sub_module_name="${module_name}-${suffix}"
    local sub_module_dir="${module_dir}/${sub_module_name}"

    if [ -d "${sub_module_dir}" ]; then
      error_exit "Sub-module ${sub_module_name} already exists"
    fi

    echo
    echo "Building sub-module ${sub_module_name}"

    echo "  Creating directories"
    #shellcheck disable=SC2086
    mkdir -p ${sub_module_dir}/src/{main,test}/{java,resources}

    echo "  Creating .gitkeep files"
    #shellcheck disable=SC2086
    touch ${sub_module_dir}/src/{main,test}/{java,resources}/.gitkeep

    local readme_file="${module_dir}/README.md"
    if [ ! -f "${readme_file}" ] ;then
      echo "  Creating module README"
      echo "# ${module_name}" > "${readme_file}"
    else
      echo "  README file already exists"
    fi

    # Replace - with .
    local gradle_module_name="${sub_module_name//-/.}"
    local gradle_build_file="${sub_module_dir}/build.gradle"

    echo "  Creating gradle build file ${gradle_build_file} for module ${gradle_module_name}"

    {
      echo -e "ext.moduleName = '${gradle_module_name}'" 
      echo -e ""
      echo -e "dependencies {"
      echo -e ""
      echo -e "}"
    } > "${gradle_build_file}"

    settings_content="${settings_content}\ninclude '${module_name}:${sub_module_name}'"

  done

  echo -e "Add the following to settings.gradle\n"
  echo "--------------------------------------------------------------------------------"
  echo -e "${settings_content}"
  echo "--------------------------------------------------------------------------------"

  echo
  echo "Done"
}


main "$@"
