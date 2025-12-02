#!/usr/bin/env bash

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

debug_value() {
  local name="$1"; shift
  local value="$1"; shift
  
  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: ${value}${NC}"
  fi
}

debug() {
  local str="$1"; shift
  
  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${str}${NC}"
  fi
}

main() {
  IS_DEBUG=false
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  setup_echo_colours

  docker \
    run \
    --detach \
    -p 9000:9000 \
    -p 9001:9001 \
    quay.io/minio/minio server \
    /data --console-address ":9001"

  echo -e "In Stroom set the Volume config to something like:"
  echo -e "${BLUE}"
  echo -e '{'
  echo -e '  "region" : "us-east-1",'
  echo -e '  "endpointOverride" : "127.0.0.1:9000",'
  echo -e '  "numRetries" : 10,'
  echo -e '  "async" : false,'
  echo -e '  "multipart" : true,'
  echo -e '  "createBuckets" : true,'
  echo -e '  "bucketName" : "bkt1",'
  echo -e '  "keyPattern" : "${feed}/${type}/${year}/${idPath}",'
  echo -e '  "credentials" : {'
  echo -e '    "type" : "basic",'
  echo -e '    "accessKeyId" : "minioadmin",'
  echo -e '    "secretAccessKey" : "minioadmin"'
  echo -e '  },'
  echo -e '}'
  echo -e "${NC}"
}

main "$@"
