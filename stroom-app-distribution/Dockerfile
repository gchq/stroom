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


FROM openjdk:8

# IN_DOCKER tells setup.sh to run Configure without asking for user input, i.e. using defaults.
ENV IN_DOCKER="true"

RUN mkdir /usr/stroom-app
ADD . /usr/stroom-app
WORKDIR /usr/stroom-app

RUN ./bin/setup.sh

EXPOSE 8080
CMD ./bin/start.sh && mkdir -p ./instance/logs && touch ./instance/logs/stroom.log && tail -f ./instance/logs/stroom.log