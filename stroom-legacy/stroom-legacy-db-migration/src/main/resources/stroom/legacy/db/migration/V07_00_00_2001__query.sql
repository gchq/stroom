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

--
-- Rename the old QUERY table, idempotent
-- MUST BE DONE HERE DUE TO NAME CLASH
--
DROP PROCEDURE IF EXISTS rename_query;
DELIMITER //
CREATE PROCEDURE rename_query ()
BEGIN
    IF NOT EXISTS (
        SELECT TABLE_NAME
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_NAME = 'OLD_QUERY') THEN

        IF EXISTS (
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'QUERY') THEN

            RENAME TABLE QUERY TO OLD_QUERY;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_query();
DROP PROCEDURE rename_query;

--
-- Create the query table
-- MUST BE DONE HERE DUE TO NAME CLASH
--
CREATE TABLE IF NOT EXISTS query (
    id                    int(11) NOT NULL AUTO_INCREMENT,
    version               int(11) NOT NULL,
    create_time_ms        bigint(20) NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint(20) NOT NULL,
    update_user           varchar(255) NOT NULL,
    dashboard_uuid        varchar(255) NOT NULL,
    component_id          varchar(255) NOT NULL,
    name                  varchar(255) NOT NULL,
    data                  longtext,
    favourite             tinyint(1) NOT NULL DEFAULT '0',
    PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the query table
-- MUST BE DONE HERE DUE TO NAME CLASH
--
DROP PROCEDURE IF EXISTS copy_query;
DELIMITER //
CREATE PROCEDURE copy_query ()
BEGIN
    IF EXISTS (
        SELECT TABLE_NAME
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_NAME = 'OLD_QUERY') THEN

        INSERT INTO query (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            dashboard_uuid,
            component_id,
            name,
            data,
            favourite)
        SELECT
            ID,
            1,
            IFNULL(CRT_MS,  0),
            IFNULL(CRT_USER,  'UNKNOWN'),
            IFNULL(UPD_MS,  0),
            IFNULL(UPD_USER,  'UNKNOWN'),
            DASH_UUID,
            QUERY_ID,
            NAME,
            DAT,
            FAVOURITE
        FROM OLD_QUERY
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM query)
        AND DASH_UUID IS NOT NULL
        AND QUERY_ID IS NOT NULL
        AND NAME IS NOT NULL
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE query AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM query;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_query();
DROP PROCEDURE copy_query;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
