#!/usr/bin/env bash

#**********************************************************************
# Copyright 2018 Crown Copyright
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

#Stop script on first error
set -e

if [ $# -ne 1 ]; then
    echo "Must supply the version as the first argument, e.g. $0 v0.1-LATEST"
    exit 1
fi
ver="$1"

cd "$(dirname "$0")"
mkdir -p work
cp ../package.json work/
cp ../.env work/
cp -r ../src work/
cp -r ../public work/

docker build --tag gchq/stroom-ui:"${ver}" .