#!/bin/bash

# Generate typescript API from Swagger schema.
npx swagger-typescript-api -p ../stroom-app/src/main/resources/ui/noauth/swagger/swagger.json -o ./src/api -n stroom.ts

yarn install
yarn build