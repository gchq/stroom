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
  SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  setup_echo_colours

  echo -e "${GREEN}In Stroom set the Volume config to something like:${NC}"
  echo -en "${BLUE}"
  echo -e '{'
  echo -e '  "region" : "us-east-1",'
  echo -e '  "endpointOverride" : "127.0.0.1:9000",'
  echo -e '  "numRetries" : 10,'
  echo -e '  "async" : false,'
  echo -e '  "multipart" : true,'
  echo -e '  "createBuckets" : true,'
  echo -e '  "bucketName" : "bkt1",'
  # shellcheck disable=SC2016
  echo -e '  "keyPattern" : "${feed}/${type}/${year}/${idPath}",'
  echo -e '  "credentials" : {'
  echo -e '    "type" : "basic",'
  echo -e '    "accessKeyId" : "minioadmin",'
  echo -e '    "secretAccessKey" : "minioadmin"'
  echo -e '  },'
  echo -e '}'
  echo -en "${NC}"

  echo
  echo -e "${GREEN}Data is persisted to docker managed volume ${BLUE}minio-data${GREEN}:${NC}"
  echo -e "${YELLOW}/var/lib/docker/volumes/minio-s3_minio-data/_data/${NC}"
  echo

  #docker \
    #run \
    #--name minio-s3 \
    #-p 9000:9000 \
    #-p 9001:9001 \
    #quay.io/minio/minio \
    #server /data --console-address ":9001"

  docker compose \
    --file "${SCRIPT_DIR}/start_s3.yml" \
    --project-name "minio-s3" \
    up
}

main "$@"
