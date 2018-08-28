#!/usr/bin/env bash

#**********************************************************************
# Copyright 2016 Crown Copyright
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
#**********************************************************************

# This script is all you need to build an image of stroom-auth-service and stroom-auth-ui.

# Exclude tests because we want this to be fast. I guess you'd better test the build before releasing.
./gradlew clean build shadowJar -x test -x integrationTest

docker build --tag gchq/stroom:dev-SNAPSHOT ./stroom-app/docker
