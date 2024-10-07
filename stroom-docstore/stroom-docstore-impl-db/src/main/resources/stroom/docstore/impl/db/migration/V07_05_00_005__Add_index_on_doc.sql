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

-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

-- --------------------------------------------------

DELIMITER $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS docstore_run_sql $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE docstore_run_sql (
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

DROP PROCEDURE IF EXISTS docstore_create_non_unique_index$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE docstore_create_non_unique_index (
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
        CALL docstore_run_sql(CONCAT(
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

DELIMITER ;

-- --------------------------------------------------

-- Improve lookup by name and to remove need to hit the table when we
-- list all docs by type.
CALL docstore_create_non_unique_index(
    "doc",
    "doc_type_name_uuid_idx",
    "type, name, uuid");

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS docstore_create_non_unique_index;

DROP PROCEDURE IF EXISTS docstore_run_sql;

-- --------------------------------------------------


-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
