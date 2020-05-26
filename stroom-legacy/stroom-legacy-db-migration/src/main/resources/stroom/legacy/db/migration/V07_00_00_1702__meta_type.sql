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
-- Create the meta_type table
--
CREATE TABLE IF NOT EXISTS meta_type (
  id            int(11) NOT NULL AUTO_INCREMENT,
  name          varchar(255) NOT NULL,
  PRIMARY KEY   (id),
  UNIQUE KEY    name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy meta into the meta_type table
--
DROP PROCEDURE IF EXISTS copy_meta_type;
DELIMITER //
CREATE PROCEDURE copy_meta_type ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'STRM_TP') THEN

        RENAME TABLE STRM_TP TO OLD_STRM_TP;
    END IF;

    IF EXISTS (
            SELECT NULL 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_NAME = 'OLD_STRM_TP') THEN

        INSERT INTO meta_type (
            id, 
            name)
        SELECT 
            ID, 
            NAME
        FROM OLD_STRM_TP
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_type)
        AND PURP IN (10, 11, 50)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE meta_type AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM meta_type;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_meta_type();
DROP PROCEDURE copy_meta_type;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
