-- ------------------------------------------------------------------------
-- Copyright 2022 Crown Copyright
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

DROP PROCEDURE IF EXISTS processor_drop_index $$

-- e.g. processor_drop_index('MY_TABLE', 'MY_IDX');
CREATE PROCEDURE processor_drop_index (
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
        CALL processor_run_sql(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop index ', p_index_name));
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS processor_create_non_unique $$

CREATE PROCEDURE processor_create_non_unique (
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
        CALL processor_run_sql(CONCAT(
            'create index ', p_index_name,
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

DROP PROCEDURE IF EXISTS processor_drop_column$$

CREATE PROCEDURE processor_drop_column (
    p_table_name varchar(64),
    p_column_name varchar(64)
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
        SELECT CONCAT(
            'Column ',
            p_column_name,
            ' does not exist on table ',
            database(),
            '.',
            p_table_name);
    ELSE
        CALL processor_run_sql(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop column ', p_column_name));
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS processor_run_sql $$

CREATE PROCEDURE processor_run_sql (
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

DELIMITER ;

-- --------------------------------------------------

CALL processor_drop_index("processor_task", "processor_task_status_idx");

CALL processor_create_non_unique(
    "processor_task",
    "processor_task_status_create_time_ms_idx",
    "status, create_time_ms");

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS processor_create_non_unique;

DROP PROCEDURE IF EXISTS processor_drop_index;

DROP PROCEDURE IF EXISTS processor_run_sql;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
