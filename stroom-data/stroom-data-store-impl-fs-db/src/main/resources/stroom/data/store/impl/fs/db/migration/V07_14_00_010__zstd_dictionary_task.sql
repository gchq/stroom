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

CREATE TABLE IF NOT EXISTS zstd_dictionary_task (
    id                        bigint NOT NULL AUTO_INCREMENT,
    create_time_ms            bigint NOT NULL,
    -- The meta_id of the file that needs re-compressing
    feed_name                 varchar(255) NOT NULL,
    -- The stream type this dict is for.
    -- Nullable to allow for a dict specific to a child stream type and feed only
    -- Max 100 chars to stop key too long errors
    stream_type_name          varchar(100) NOT NULL,
    -- The child stream type this dict is for.
    -- Nullable for the main stream type
    -- Max 100 chars to stop key too long errors
    child_stream_type_name    varchar(100),
    -- The status of this dict, e.g. if it is still in training
    meta_id                   bigint NOT NULL,
    PRIMARY KEY (id),
    -- No point having >1 task for the same file key
    UNIQUE KEY `zstd_dict_task_feed_stream_type_child_type_meta_id_idx` (
        feed_name,
        stream_type_name,
        child_stream_type_name,
        meta_id),
    KEY `zstd_dict_task_feed_stream_type_child_type_create_time_idx` (
        feed_name,
        stream_type_name,
        child_stream_type_name,
        create_time_ms)
--     CONSTRAINT `zstd_dict_task_fk_meta_id` FOREIGN KEY (`meta_id`)
--         REFERENCES `fs_meta_volume` (meta_id)
    ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
