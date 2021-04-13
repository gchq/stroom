#!/bin/bash

# Generate typescript API from Swagger schema.
# Latest version of `wagger-typescript-api` 8.0.3 but doesn't seem to work.
echo "npx swagger-typescript-api@6.4.2 -p ../stroom-app/src/main/resources/ui/noauth/swagger/stroom.json -o ./src/api -n stroom.ts"
npx swagger-typescript-api@6.4.2 -p ../stroom-app/src/main/resources/ui/noauth/swagger/stroom.json -o ./src/api -n stroom.ts