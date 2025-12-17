#!/usr/bin/env bash

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
    -Pflyway.locations=filesystem:stroom-core/src/main/resources/stroom/db/migration/mysql,classpath:stroom/db/migration/mysql \
    :stroom-app:flyway$1
