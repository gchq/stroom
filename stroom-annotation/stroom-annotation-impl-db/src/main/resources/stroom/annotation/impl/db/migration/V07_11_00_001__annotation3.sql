-- ------------------------------------------------------------------------
-- Copyright 2023 Crown Copyright
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

DROP PROCEDURE IF EXISTS V07_11_00_001_annotation;

DELIMITER $$

CREATE PROCEDURE V07_11_00_001_annotation ()
BEGIN
    DECLARE object_count integer;

    --
    -- Add entry parent id
    --
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'annotation_entry'
    AND column_name = 'parent_id';

    IF object_count = 0 THEN
        ALTER TABLE `annotation_entry` ADD COLUMN `parent_id` bigint DEFAULT NULL;
    END IF;

    --
    -- Add entry update time
    --
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'annotation_entry'
    AND column_name = 'update_time_ms';

    IF object_count = 0 THEN
        ALTER TABLE `annotation_entry` ADD COLUMN `update_time_ms` bigint(20) NOT NULL;

        -- Copy all entry times to update times.
        SET @sql_str = CONCAT(
            'UPDATE annotation_entry a ',
            'SET a.update_time_ms = a.entry_time_ms');
        PREPARE stmt FROM @sql_str;
        EXECUTE stmt;

    END IF;

    --
    -- Add entry update user
    --
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'annotation_entry'
    AND column_name = 'update_user_uuid';

    IF object_count = 0 THEN
        ALTER TABLE `annotation_entry` ADD COLUMN `update_user_uuid` varchar(255) NOT NULL;

        -- Copy all entry users to update users.
        SET @sql_str = CONCAT(
            'UPDATE annotation_entry a ',
            'SET a.update_user_uuid = a.entry_user_uuid');
        PREPARE stmt FROM @sql_str;
        EXECUTE stmt;

    END IF;

END $$

DELIMITER ;

CALL V07_11_00_001_annotation;

DROP PROCEDURE IF EXISTS V07_11_00_001_annotation;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
