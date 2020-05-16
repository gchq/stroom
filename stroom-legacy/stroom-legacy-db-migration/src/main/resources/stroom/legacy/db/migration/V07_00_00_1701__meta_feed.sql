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
-- Create the meta_feed table
--
CREATE TABLE IF NOT EXISTS meta_feed (
    id           int(11) NOT NULL AUTO_INCREMENT,
    name         varchar(255) NOT NULL,
    PRIMARY KEY  (id),
    UNIQUE KEY   name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy meta into the meta_feed table
--
DROP PROCEDURE IF EXISTS copy_meta_feed;
DELIMITER //
CREATE PROCEDURE copy_meta_feed ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'FD') THEN

        RENAME TABLE FD TO OLD_FD;
    END IF;

    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_NAME = 'OLD_FD') THEN

        INSERT INTO meta_feed (
            id, 
            name)
        SELECT 
            ID, 
            NAME
        FROM OLD_FD
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_feed)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE meta_feed AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM meta_feed;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_meta_feed();
DROP PROCEDURE copy_meta_feed;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
