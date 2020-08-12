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
-- Create the fs_feed_path table
--
CREATE TABLE IF NOT EXISTS fs_feed_path (
    id                int(11) NOT NULL AUTO_INCREMENT,
    name              varchar(255) NOT NULL,
    path              varchar(255) NOT NULL,
    PRIMARY KEY       (id),
    UNIQUE KEY        name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the fs_feed_path table
--
DROP PROCEDURE IF EXISTS copy_fs_feed_path;
DELIMITER //
CREATE PROCEDURE copy_fs_feed_path ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'FD') THEN

        RENAME TABLE FD TO OLD_FD;
    END IF;

    -- Check again so it is idempotent
    IF EXISTS (
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_FD') THEN

        INSERT INTO fs_feed_path (
            id,
            name,
            path)
        SELECT
            ID,
            NAME,
            ID
        FROM OLD_FD
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM fs_feed_path)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE fs_feed_path AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM fs_feed_path;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;

END//
DELIMITER ;
CALL copy_fs_feed_path();
DROP PROCEDURE copy_fs_feed_path;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
