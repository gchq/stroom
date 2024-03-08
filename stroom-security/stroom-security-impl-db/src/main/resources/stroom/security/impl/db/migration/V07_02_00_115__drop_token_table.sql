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

DROP PROCEDURE IF EXISTS V07_02_00_115;
DROP PROCEDURE IF EXISTS security_run_sql_v1;

DELIMITER $$

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

-- We shouldn't really be dropping a table in a different db module but we can't be sure
-- which module's migrations will run first and we have to do this after the migration
-- from token => api_key is done.
CREATE PROCEDURE V07_02_00_115 ()
BEGIN
    DECLARE object_count integer;

    select count(*)
    INTO object_count
    from information_schema.tables
    where table_name = 'token'
    and table_schema = database();

    IF object_count = 1 THEN
        CALL security_run_sql_v1('DROP TABLE token;');
    END IF;
END $$

DELIMITER ;

CALL V07_02_00_115;

DROP PROCEDURE IF EXISTS V07_02_00_115;
DROP PROCEDURE IF EXISTS security_run_sql_v1;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
