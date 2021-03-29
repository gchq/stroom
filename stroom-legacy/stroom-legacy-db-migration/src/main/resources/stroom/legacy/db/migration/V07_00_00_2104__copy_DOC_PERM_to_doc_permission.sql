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
    -- Can be run by multiple scripts
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'DOC_PERM') THEN

        RENAME TABLE DOC_PERM TO OLD_DOC_PERM;
    END IF;

    -- Can be run by multiple scripts
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'USR') THEN

        RENAME TABLE USR TO OLD_USR;
    END IF;

    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_DOC_PERM') THEN

        -- Ignore perms linked to users that don't exist
        INSERT INTO doc_permission (
            user_uuid,
            doc_uuid,
            permission)
        SELECT
            USR_UUID,
            DOC_UUID,
            PERM
        FROM OLD_DOC_PERM
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM doc_permission)
        AND EXISTS (
            SELECT NULL
            FROM OLD_USR
            WHERE UUID = USR_UUID)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE doc_permission AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM doc_permission;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_security();
DROP PROCEDURE copy_security;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
