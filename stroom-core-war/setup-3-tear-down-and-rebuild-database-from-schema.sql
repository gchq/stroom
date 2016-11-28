/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


drop database if exists ${stroom.setup.db.name};

create database ${stroom.setup.db.name};

grant all privileges on ${stroom.setup.db.name}.* to ${stroom.jdbcDriverUsername}@localhost identified by '${stroom.jdbcDriverPassword}';

