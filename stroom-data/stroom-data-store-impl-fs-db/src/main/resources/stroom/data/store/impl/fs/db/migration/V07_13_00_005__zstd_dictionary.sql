/*
 * Copyright 2016-2026 Crown Copyright
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

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- One dictionary (i.e one dict uuid) can be associated with multiple
-- feed|stream_type_name|child_stream_type_name combinations

CREATE TABLE IF NOT EXISTS zstd_dictionary (
    id                        int NOT NULL AUTO_INCREMENT,
    version                   int NOT NULL, -- OCC version
    create_time_ms            bigint NOT NULL,
    create_user               varchar(255) NOT NULL,
    update_time_ms            bigint NOT NULL,
    update_user               varchar(255) NOT NULL,
    -- The feed this dict is for
    feed_name                 varchar(255) NOT NULL,
    -- The stream type this dict is for.
    -- Nullable to allow for a dict specific to a child stream type and feed only
    -- Max 100 chars to stop key too long errors
    stream_type_name          varchar(100),
    -- The child stream type this dict is for.
    -- Nullable for the main stream type
    -- Max 100 chars to stop key too long errors
    child_stream_type_name    varchar(100),
    -- The unique identifier of the dictionary that the combination of
    -- feed|stream_type_name|child_stream_type_name is associated with.
    uuid                      varchar(36) NOT NULL,
    -- The status of this dict, e.g. if it is still in training
    status                    tinyint NOT NULL,
    PRIMARY KEY (id),
    KEY `zstd_dict_feed_type_child_type_uuid_idx` (
        feed_name,
        stream_type_name,
        child_stream_type_name,
        uuid),
    KEY `zstd_dict_feed_type_uuid_idx` (
        feed_name,
        stream_type_name,
        uuid),
    KEY `zstd_dict_uuid_idx` (uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
