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

DROP PROCEDURE IF EXISTS data_store_run_sql_v1 $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE data_store_run_sql_v1 (
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

DROP PROCEDURE IF EXISTS data_store_add_column_v1$$

CREATE PROCEDURE data_store_add_column_v1 (
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
        CALL data_store_run_sql_v1(CONCAT(
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

DROP PROCEDURE IF EXISTS data_store_create_unique_index_v1$$

CREATE PROCEDURE data_store_create_unique_index_v1 (
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
        CALL data_store_run_sql_v1(CONCAT(
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

DROP PROCEDURE IF EXISTS V07_04_00_010$$

-- All of this is idempotent
CREATE PROCEDURE V07_04_00_010 ()
BEGIN
    DECLARE default_uuid VARCHAR(40);
    DECLARE object_count integer;
    SET default_uuid = 'dcf96afb-78a0-4a54-830f-0e3ae998f4af';

    -- Delete the default vol group created by V07_03_00_001__fs_volume_s3.sql
    -- as it shouldn't have been, but only if it has no child vols
    DELETE FROM fs_volume_group fvg
    WHERE NOT EXISTS (
        SELECT NULL
        FROM fs_volume fv
        WHERE fv.fk_fs_volume_group_id = fvg.id
    )
    AND fvg.name = 'Default Volume Group';

    SELECT COUNT(*)
    INTO object_count
    FROM fs_volume_group
    WHERE name = "Default Volume Group";

    -- name is unique
    IF object_count = 0 THEN
        SELECT COUNT(*)
        INTO object_count
        FROM fs_volume_group;

        IF object_count = 1 THEN
            -- Only one vol grp, but it is called something else
            -- Give it the hard coded UUID from
            -- stroom.data.store.impl.fs.shared.FsVolumeGroup.DEFAULT_VOLUME_UUID
            UPDATE fs_volume_group
            SET
                uuid = default_uuid,
                is_default = true;
        END IF;
    ELSE
        -- Give it the hard coded UUID from
        -- stroom.data.store.impl.fs.shared.FsVolumeGroup.DEFAULT_VOLUME_UUID
        UPDATE fs_volume_group
        SET
            uuid = default_uuid,
            is_default = true
        WHERE name = 'Default Volume Group';
    END IF;

    -- Now give all remaining vol groups a random uuid so we can add a constraint on
    UPDATE fs_volume_group
    SET uuid = UUID()
    WHERE uuid IS NULL;

    ALTER TABLE fs_volume_group
    MODIFY COLUMN uuid varchar(255) NOT NULL;

END $$

-- --------------------------------------------------

DELIMITER ;

-- Add a column to state whether the volume group is the default one to use if
-- no volume group has been specified.
CALL data_store_add_column_v1(
    'fs_volume_group',
    'is_default',
    'tinyint');

CALL data_store_create_unique_index_v1(
    'fs_volume_group',
    'is_default_idx',
    'is_default');

CALL data_store_add_column_v1(
    'fs_volume_group',
    'uuid',
    'varchar(255)'); -- Nullable initially, assign uuids below

CALL data_store_create_unique_index_v1(
    'fs_volume_group',
    'uuid_idx',
    'uuid');

-- Now migrate the data in the table
CALL V07_04_00_010;

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS data_store_add_column_v1;

DROP PROCEDURE IF EXISTS data_store_run_sql_v1 ;

DROP PROCEDURE IF EXISTS data_store_create_unique_index_v1;

DROP PROCEDURE IF EXISTS V07_04_00_010;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
