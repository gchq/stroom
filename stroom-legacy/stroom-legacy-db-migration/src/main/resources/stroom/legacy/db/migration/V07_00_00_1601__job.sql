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
-- Create the job table
--
CREATE TABLE IF NOT EXISTS job (
    id                    int(11) NOT NULL AUTO_INCREMENT,
    version               int(11) NOT NULL,
    create_time_ms        bigint(20) NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint(20) NOT NULL,
    update_user           varchar(255) NOT NULL,
    name                  varchar(255) NOT NULL,
    enabled               tinyint(1) NOT NULL DEFAULT '0',
    PRIMARY KEY           (id),
    UNIQUE KEY name       (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the job table
-- MUST BE DONE HERE DUE TO NAME CLASH
--
DROP PROCEDURE IF EXISTS copy_job;
DELIMITER //
CREATE PROCEDURE copy_job ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'JB') THEN

        RENAME TABLE JB TO OLD_JB;
    END IF;

    -- Check again so it is idempotent
    IF EXISTS (
        SELECT TABLE_NAME
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_NAME = 'OLD_JB') THEN

        INSERT INTO job (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            name,
            enabled)
        SELECT
            ID,
            1,
            IFNULL(CRT_MS,  0),
            IFNULL(CRT_USER,  'UNKNOWN'),
            IFNULL(UPD_MS,  0),
            IFNULL(UPD_USER,  'UNKNOWN'),
            NAME,
            ENBL
        FROM OLD_JB
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM job)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE job AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM job;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_job();
DROP PROCEDURE copy_job;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
