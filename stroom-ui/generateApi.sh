#!/bin/bash
set -eo pipefail

echo -e "Generating Swagger API"

# Ensure we are in the dir where this script lives
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
pushd "${SCRIPT_DIR}" > /dev/null

# Generate typescript API from Swagger schema.
# Latest version of `wagger-typescript-api` 8.0.3 but doesn't seem to work.
echo -e \
  "npx npm-force-resolutions\n" \
  "  && npx\n" \
  "    swagger-typescript-api@6.4.2\n" \
  "    -p ../../stroom-app/src/main/resources/ui/noauth/swagger/stroom.json\n" \
  "    -o ../src/api\n" \
  "    -n stroom.ts\n"

# swagger_typescript_api_root is so that we can use a package.json file that forces
# the version of the typescript dep to 4.7.4, 
# see https://github.com/acacode/swagger-typescript-api/issues/370
pushd "swagger_typescript_api_root" > /dev/null

echo "PWD: $PWD"

npx npm-force-resolutions \
  && npx \
    swagger-typescript-api@6.4.2 \
    -p ../../stroom-app/src/main/resources/ui/noauth/swagger/stroom.json \
    -o ../src/api \
    -n stroom.ts

echo -e "Completed Swagger API generation"
