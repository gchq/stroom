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

DROP PROCEDURE IF EXISTS index_drop_column_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE index_drop_column_v1 (
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
        CALL index_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop column ', p_column_name));
    END IF;
END $$

-- --------------------------------------------------

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

DELIMITER ;

-- --------------------------------------------------

CALL index_drop_column_v1("index_shard", "old_index_id");

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS index_drop_column_v1;

DROP PROCEDURE IF EXISTS index_run_sql_v1;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
