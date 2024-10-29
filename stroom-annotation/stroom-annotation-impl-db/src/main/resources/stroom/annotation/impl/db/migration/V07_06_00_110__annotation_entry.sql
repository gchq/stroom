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

DROP PROCEDURE IF EXISTS V07_06_00_110__annotation_entry;

DELIMITER $$

CREATE PROCEDURE V07_06_00_110__annotation_entry ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'annotation_entry'
    AND column_name = 'entry_user_uuid';

    IF object_count = 0 THEN
        ALTER TABLE annotation_entry ADD COLUMN entry_user_uuid varchar(255) DEFAULT NULL;
        ALTER TABLE annotation_entry ADD COLUMN entry_time_ms bigint NOT NULL;

        SELECT COUNT(1)
        INTO object_count
        FROM information_schema.tables
        WHERE table_schema = database()
        AND table_name = 'stroom_user';

        IF object_count = 1 THEN
            -- Change create user names to entry user uuids.
            SET @sql_str = CONCAT(
                'UPDATE annotation_entry a, stroom_user s ',
                'SET a.entry_user_uuid = s.uuid ',
                'WHERE a.create_user = s.name');
            PREPARE stmt FROM @sql_str;
            EXECUTE stmt;

            -- Move all create times to entry times.
            SET @sql_str = CONCAT(
                'UPDATE annotation_entry a ',
                'SET a.entry_time_ms = a.create_time_ms');
            PREPARE stmt FROM @sql_str;
            EXECUTE stmt;

            -- Change all assignment entries to reference user UUID instead of name.
            SET @sql_str = CONCAT(
                'UPDATE annotation_entry a, stroom_user s ',
                'SET a.data = s.uuid ',
                'WHERE a.type = "Assigned" AND a.data = s.name');
            PREPARE stmt FROM @sql_str;
            EXECUTE stmt;
        END IF;

        ALTER TABLE annotation_entry DROP COLUMN version;
        ALTER TABLE annotation_entry DROP COLUMN create_time_ms;
        ALTER TABLE annotation_entry DROP COLUMN create_user;
        ALTER TABLE annotation_entry DROP COLUMN update_time_ms;
        ALTER TABLE annotation_entry DROP COLUMN update_user;

    END IF;
END $$

DELIMITER ;

CALL V07_06_00_110__annotation_entry;

DROP PROCEDURE IF EXISTS V07_06_00_110__annotation_entry;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
