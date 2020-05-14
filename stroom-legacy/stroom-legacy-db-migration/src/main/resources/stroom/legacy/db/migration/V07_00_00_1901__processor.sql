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

--Create Table: CREATE TABLE `STRM_PROC` (
--  `ID` int(11) NOT NULL AUTO_INCREMENT,
--  `VER` tinyint(4) NOT NULL,
--  `CRT_USER` varchar(255) DEFAULT NULL,
--  `UPD_USER` varchar(255) DEFAULT NULL,
--  `TASK_TP` varchar(255) DEFAULT NULL,
--  `FK_PIPE_ID` int(11) DEFAULT NULL,
--  `ENBL` bit(1) NOT NULL,
--  `CRT_MS` bigint(20) DEFAULT NULL,
--  `UPD_MS` bigint(20) DEFAULT NULL,
--  PRIMARY KEY (`ID`),
--  KEY `STRM_PROC_FK_PIPE_ID` (`FK_PIPE_ID`),
--  CONSTRAINT `STRM_PROC_FK_PIPE_ID` FOREIGN KEY (`FK_PIPE_ID`) REFERENCES `PIPE` (`ID`)
--) ENGINE=InnoDB DEFAULT CHARSET=latin1

-- Create the table
CREATE TABLE IF NOT EXISTS processor (
    id                    int(11) NOT NULL AUTO_INCREMENT,
    version               int(11) NOT NULL,
    create_time_ms        bigint(20) NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint(20) NOT NULL,
    update_user           varchar(255) NOT NULL,
    uuid                  varchar(255) NOT NULL,
    task_type             varchar(255) DEFAULT NULL,
    pipeline_uuid         varchar(255) NOT NULL,
    enabled               bit(1) DEFAULT 0,
    deleted               bit(1) DEFAULT 0,
    PRIMARY KEY           (id),
    UNIQUE KEY            uuid (uuid),
    UNIQUE KEY            pipeline_uuid (pipeline_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS copy_processor;
DELIMITER //
CREATE PROCEDURE copy_processor ()
BEGIN
    -- idempotent, doesn't matter if two scripts try to do this
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'STRM_PROC') THEN

        RENAME TABLE STRM_PROC TO OLD_STRM_PROC;
    END IF;

    -- idempotent, doesn't matter if two scripts try to do this
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'PIPE') THEN

        RENAME TABLE PIPE TO OLD_PIPE;
    END IF;

    -- Check again so it is idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_STRM_PROC') THEN
        --
        -- Copy data into the table, use ID predicate to make it re-runnable
        --
        INSERT INTO processor (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            uuid,
            task_type,
            pipeline_uuid,
            enabled)
        SELECT
            SP.ID,
            SP.VER,
            IFNULL(SP.CRT_MS,  0),
            IFNULL(SP.CRT_USER,  'UNKNOWN'),
            IFNULL(SP.UPD_MS,  0),
            IFNULL(SP.UPD_USER,  'UNKNOWN'),
            P.UUID,
            SP.TASK_TP,
            P.UUID,
            SP.ENBL
        FROM OLD_STRM_PROC SP
        INNER JOIN OLD_PIPE P ON (P.ID = SP.FK_PIPE_ID)
        WHERE SP.ID > (SELECT COALESCE(MAX(id), 0) FROM processor)
        ORDER BY SP.ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;

END//
DELIMITER ;
CALL copy_processor();
DROP PROCEDURE copy_processor;

SET SQL_NOTES=@OLD_SQL_NOTES;
