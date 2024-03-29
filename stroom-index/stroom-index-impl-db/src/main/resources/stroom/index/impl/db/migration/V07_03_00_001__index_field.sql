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

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS drop_field_source;
DELIMITER //
CREATE PROCEDURE drop_field_source ()
BEGIN
    IF EXISTS (
        SELECT NULL
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'field_info') THEN
        DROP TABLE field_info;
    END IF;
    IF EXISTS (
        SELECT NULL
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'field_source') THEN
        DROP TABLE field_source;
    END IF;
    IF EXISTS (
        SELECT NULL
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'field_schema_history') THEN
        DROP TABLE field_schema_history;
    END IF;
END//
DELIMITER ;
CALL drop_field_source();
DROP PROCEDURE drop_field_source;

--
-- Create the field_source table
--
CREATE TABLE IF NOT EXISTS `index_field_source` (
    `id`        int NOT NULL AUTO_INCREMENT,
    `type`      varchar(255) NOT NULL,
    `uuid`      varchar(255) NOT NULL,
    `name`      varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY  `index_field_source_type_uuid` (`type`, `uuid`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Create the index_field table
--
CREATE TABLE IF NOT EXISTS `index_field` (
    `id`                        bigint NOT NULL AUTO_INCREMENT,
    `fk_index_field_source_id`  int NOT NULL,
    `type`                      tinyint NOT NULL,
    `name`                      varchar(255) NOT NULL,
    `analyzer`                  varchar(255) NOT NULL,
    `indexed`                   tinyint NOT NULL DEFAULT '0',
    `stored`                    tinyint NOT NULL DEFAULT '0',
    `term_positions`            tinyint NOT NULL DEFAULT '0',
    `case_sensitive`            tinyint NOT NULL DEFAULT '0',
    PRIMARY KEY                 (`id`),
    UNIQUE KEY                  `index_field_source_id_name` (`fk_index_field_source_id`, `name`),
    CONSTRAINT `index_field_fk_index_field_source_id` FOREIGN KEY (`fk_index_field_source_id`) REFERENCES `index_field_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
