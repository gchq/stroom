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

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS security_run_sql $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE security_run_sql (
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

DROP PROCEDURE IF EXISTS security_create_unique_index$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE security_create_unique_index (
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
        CALL security_run_sql(CONCAT(
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

DROP PROCEDURE IF EXISTS security_drop_index $$

-- e.g. security_drop_index('MY_TABLE', 'MY_IDX');
CREATE PROCEDURE security_drop_index (
    p_table_name varchar(64),
    p_index_name varchar(64)
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
        SELECT CONCAT(
            'Index ',
            p_index_name,
            ' does not exist on table ',
            database(),
            '.',
            p_table_name);
    ELSE
        CALL security_run_sql(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop index ', p_index_name));
    END IF;
END $$

-- --------------------------------------------------

DELIMITER ;

-- --------------------------------------------------

-- We need to make this column case sensitive (_as_cs) else we limit the range of keys we can generate
-- as the keys have mixed case.
-- Note the api_key_prefix col contains lower case data so can stay as _ai_ci
ALTER TABLE api_key MODIFY
    api_key_hash VARCHAR(255)
      CHARACTER SET utf8mb4
      COLLATE utf8mb4_0900_as_cs
      NOT NULL;

CALL security_drop_index(
    "api_key",
    "api_key_prefix_idx");

CALL security_drop_index(
    "api_key",
    "api_key_api_key_hash_idx");

-- Drop the index we are about to create to make the script idempotent
CALL security_drop_index(
    "api_key",
    "api_key_prefix_hash_idx");

-- All we have to lookup by are the prefix and the hash of the key
-- so they must be unique between them.
-- A hash and prefix clash is pretty unlikely but possible
CALL security_create_unique_index(
    "api_key",
    "api_key_prefix_hash_idx",
    "api_key_prefix, api_key_hash");

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS security_create_unique_index;

DROP PROCEDURE IF EXISTS security_drop_index;

DROP PROCEDURE IF EXISTS security_run_sql;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
