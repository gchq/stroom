-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- stop note level warnings about objects (not)? existing
set @old_sql_notes = @@sql_notes, sql_notes = 0;

CREATE TABLE IF NOT EXISTS `explorer_favourite`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `doc_type`       varchar(255) NOT NULL,
    `doc_uuid`       varchar(255) NOT NULL,
    `user_uuid`      varchar(255) NOT NULL,
    `create_time_ms` bigint       NOT NULL,
    PRIMARY KEY (`id`),
    KEY `explorer_favourite_fk_doc_type_doc_uuid` (`doc_type`, `doc_uuid`),
    KEY `explorer_favourite_fk_user_uuid` (`user_uuid`),
    UNIQUE KEY `explorer_favourite_fk_en_type_en_uuid_user_uuid` (`doc_type`, `doc_uuid`, `user_uuid`),
    CONSTRAINT `explorer_favourite_fk_en_type_en_uuid` FOREIGN KEY (`doc_type`, `doc_uuid`)
        REFERENCES `stroom`.`explorer_node` (`type`, `uuid`)
        ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 446
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- Reset to the original value
SET SQL_NOTES = @OLD_SQL_NOTES;
