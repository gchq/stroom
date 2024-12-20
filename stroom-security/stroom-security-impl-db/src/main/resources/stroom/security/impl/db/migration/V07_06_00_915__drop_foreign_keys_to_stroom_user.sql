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

DROP PROCEDURE IF EXISTS security_run_sql_v1 $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE security_run_sql_v1 (
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

DROP PROCEDURE IF EXISTS security_drop_constraint_v1 $$

-- e.g. security_drop_constraint_v1('MY_TABLE', 'MY_FK', 'FOREIGN KEY');
--      security_drop_constraint_v1('MY_TABLE', 'MY_UNIQ_IDX', 'INDEX');
--      security_drop_constraint_v1('MY_TABLE', 'PRIMARY', 'INDEX');
-- DO NOT change this without reading the header!
CREATE PROCEDURE security_drop_constraint_v1 (
    p_table_name varchar(64),
    p_constraint_name varchar(64),
    p_constraint_type varchar(64) -- e.g. FOREIGN KEY | UNIQUE
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.table_constraints
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND constraint_name = p_constraint_name;

    IF object_count = 0 THEN
        SELECT CONCAT(
            'Constraint ',
            p_constraint_name,
            ' does not exist on table ',
            database(),
            '.',
            p_table_name);
    ELSE
        CALL security_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop ', p_constraint_type, ' ', p_constraint_name));
    END IF;
END $$

DELIMITER ;

-- --------------------------------------------------

CALL security_drop_constraint_v1(
    'app_permission',
    'app_permission_user_uuid',
    'FOREIGN KEY');

CALL security_drop_constraint_v1(
    'doc_permission',
    'doc_permission_fk_user_uuid',
    'FOREIGN KEY');

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS security_run_sql_v1;

DROP PROCEDURE IF EXISTS security_drop_constraint_v1;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
