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
-- Copy data into the permissions tables
--
DROP PROCEDURE IF EXISTS copy_security;
DELIMITER //
CREATE PROCEDURE copy_security ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'USR') THEN

        RENAME TABLE USR TO OLD_USR;
    END IF;

    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_USR') THEN

        INSERT INTO stroom_user (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            name,
            uuid,
            is_group,
            enabled)
        SELECT
            U.ID,
            1,
            IFNULL(U.CRT_MS,  0),
            IFNULL(U.CRT_USER,  'UNKNOWN'),
            IFNULL(U.UPD_MS,  0),
            IFNULL(U.UPD_USER,  'UNKNOWN'),
            U.NAME,
            U.UUID,
            U.GRP,
            (CASE U.STAT WHEN 0 THEN true ELSE false END)
        FROM OLD_USR U
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM stroom_user)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE stroom_user AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM stroom_user;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_security();
DROP PROCEDURE copy_security;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
