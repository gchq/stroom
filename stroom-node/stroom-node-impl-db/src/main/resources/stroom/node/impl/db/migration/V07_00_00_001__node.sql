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
-- Create the node table
--
CREATE TABLE IF NOT EXISTS node (
    id                    int(11) NOT NULL AUTO_INCREMENT,
    version               int(11) NOT NULL,
    create_time_ms        bigint(20) NOT NULL,
    create_user           varchar(255) NOT NULL,
    update_time_ms        bigint(20) NOT NULL,
    update_user           varchar(255) NOT NULL,
    url                   varchar(255) DEFAULT NULL,
    name                  varchar(255) NOT NULL,
    priority              smallint(6) NOT NULL,
    enabled               bit(1) NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the node table
--
DROP PROCEDURE IF EXISTS copy_node;
DELIMITER //
CREATE PROCEDURE copy_node ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'ND' > 0) THEN
        INSERT INTO node (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            url,
            name,
            priority,
            enabled)
        SELECT
            ID,
            VER,
            IFNULL(CRT_MS,  0),
            IFNULL(CRT_USER,  'UNKNOWN'),
            IFNULL(UPD_MS,  0),
            IFNULL(UPD_USER,  'UNKNOWN'),
            CLSTR_URL,
            NAME,
            PRIOR,
            ENBL
        FROM ND
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM node)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE node AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM node;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_node();
DROP PROCEDURE copy_node;

SET SQL_NOTES=@OLD_SQL_NOTES;
