#!/usr/bin/env bash

# Exit script on any error
set -e

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
NC='\033[0m' # No Colour


NO_ARGUMENT_MESSAGE="Please supply an argument: either ${YELLOW}migrate${NC}, ${YELLOW}clean${NC}, ${YELLOW}info${NC}, ${YELLOW}validate${NC}, ${YELLOW}undo${NC}, ${YELLOW}baseline${NC} or ${YELLOW}repair${NC}."
# Check script's params
if [ $# -ne 1 ]; then
    echo -e $NO_ARGUMENT_MESSAGE
    echo -e ""
    exit 1
fi

# The table Flyway uses to record it's migrations has had it's name changed in 5.0.0. So we need to set it below.
./gradlew compileJava \
    -Pflyway.user=stroomuser \
    -Pflyway.password=stroompassword1 \
    -Pflyway.url=jdbc:mysql://localhost:3307/stroom \
    -Pflyway.table=schema_version \
    -Pflyway.locations=filesystem:stroom-core-server/src/main/resources/stroom/db/migration/mysql,classpath:stroom/db/migration/mysql \
    :stroom-app:flyway$1
