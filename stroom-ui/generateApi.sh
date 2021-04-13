#!/bin/bash

# Generate typescript API from Swagger schema.
echo "npx --version"
npx --version
echo "npx swagger-typescript-api@8.0.3 -p ../stroom-app/src/main/resources/ui/noauth/swagger/stroom.json -o ./src/api -n stroom.ts"
npx swagger-typescript-api@8.0.3 -p ../stroom-app/src/main/resources/ui/noauth/swagger/stroom.json -o ./src/api -n stroom.ts