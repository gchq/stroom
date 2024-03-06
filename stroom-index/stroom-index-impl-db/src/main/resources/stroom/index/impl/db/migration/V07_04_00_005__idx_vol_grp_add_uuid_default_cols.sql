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

-- --------------------------------------------------

DELIMITER $$

DROP PROCEDURE IF EXISTS index_run_sql_v1 $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE index_run_sql_v1 (
    p_sql_stmt varchar(1000)
)
BEGIN

    SET @sqlstmt = p_sql_stmt;

    SELECT CONCAT('Running sql: ', @sqlstmt);

    PREPARE stmt FROM @sqlstmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS index_add_column_v1$$

CREATE PROCEDURE index_add_column_v1 (
    p_table_name varchar(64),
    p_column_name varchar(64),
    p_column_type_info varchar(64) -- e.g. 'varchar(255) default NULL'
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND column_name = p_column_name;

    IF object_count = 0 THEN
        CALL index_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' add column ', p_column_name, ' ', p_column_type_info));
    ELSE
        SELECT CONCAT(
            'Column ',
            p_column_name,
            ' already exists on table ',
            database(),
            '.',
            p_table_name);
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS index_create_unique_index_v1$$

CREATE PROCEDURE index_create_unique_index_v1 (
    p_table_name varchar(64),
    p_index_name varchar(64),
    p_index_columns varchar(64)
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND index_name = p_index_name;

    IF object_count = 0 THEN
        CALL index_run_sql_v1(CONCAT(
            'create unique index ', p_index_name,
            ' on ', database(), '.', p_table_name,
            ' (', p_index_columns, ')'));
    ELSE
        SELECT CONCAT(
            'Index ',
            p_index_name,
            ' already exists on table ',
            database(),
            '.',
            p_table_name);
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS V07_04_00_005$$

-- All of this is idempotent
CREATE PROCEDURE V07_04_00_005 ()
BEGIN
    DECLARE default_uuid VARCHAR(40);
    DECLARE object_count integer;
    SET default_uuid = '5de2d603-cfc7-45cf-a8b4-e06bdf454f5e';

    SELECT COUNT(*)
    INTO object_count
    FROM index_volume_group
    WHERE name = "Default Volume Group";

    -- name is unique
    IF object_count = 0 THEN
        SELECT COUNT(*)
        INTO object_count
        FROM index_volume_group;

        IF object_count = 1 THEN
            -- Only one vol grp, but it is called something else
            -- Give it the hard coded UUID from
            -- stroom.index.shared.IndexVolumeGroup#DEFAULT_VOLUME_UUID
            UPDATE index_volume_group
            SET
                uuid = default_uuid,
                is_default = true;
        END IF;
    ELSE
        -- 'Default Volume Group' exists so, give it the hard coded UUID from
        -- stroom.index.shared.IndexVolumeGroup#DEFAULT_VOLUME_UUID
        UPDATE index_volume_group
        SET
            uuid = default_uuid,
            is_default = true
        WHERE name = 'Default Volume Group';
    END IF;

    -- Now give all remaining vol groups a random uuid so we can add a constraint on
    UPDATE index_volume_group
    SET uuid = UUID()
    WHERE uuid IS NULL;

    ALTER TABLE index_volume_group
    MODIFY COLUMN uuid varchar(255) NOT NULL;

END $$

DELIMITER ;

-- --------------------------------------------------

-- Add a column to state whether the volume group is the default one to use if
-- no volume group has been specified.
CALL index_add_column_v1(
    'index_volume_group',
    'is_default',
    'tinyint');

-- Ensure only one volume group c
CALL index_create_unique_index_v1(
    'index_volume_group',
    'is_default_idx',
    'is_default');

CALL index_add_column_v1(
    'index_volume_group',
    'uuid',
    'varchar(255)'); -- Nullable initially, assign uuids in Java

CALL index_create_unique_index_v1(
    'index_volume_group',
    'uuid_idx',
    'uuid');

-- Now migrate the data in the table
CALL V07_04_00_005;

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS index_add_column_v1;

DROP PROCEDURE IF EXISTS index_run_sql_v1 ;

DROP PROCEDURE IF EXISTS index_create_unique_index_v1;

DROP PROCEDURE IF EXISTS V07_04_00_005;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
