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

-- stop note level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Rename the old QUERY table, idempotent
--
DROP PROCEDURE IF EXISTS rename_query;
DELIMITER //
CREATE PROCEDURE rename_query ()
BEGIN
    IF NOT EXISTS (
        SELECT TABLE_NAME
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'OLD_QUERY') THEN

        IF EXISTS (
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'QUERY') THEN

            RENAME TABLE QUERY TO OLD_QUERY;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_query();
DROP PROCEDURE rename_query;

--
-- Rename the old DASH table, idempotent
--
DROP PROCEDURE IF EXISTS rename_dash;
DELIMITER //
CREATE PROCEDURE rename_dash ()
BEGIN
    IF NOT EXISTS (
        SELECT TABLE_NAME
        FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = database()
        AND TABLE_NAME = 'OLD_DASH') THEN

        IF EXISTS (
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'DASH') THEN

            RENAME TABLE DASH TO OLD_DASH;
        END IF;
    END IF;
END//
DELIMITER ;
CALL rename_dash();
DROP PROCEDURE rename_dash;

-- idempotent
CALL core_add_column_v1(
    'OLD_QUERY',
    'DASH_UUID',
    'varchar(255) default NULL');

-- idempotent
UPDATE OLD_QUERY Q
INNER JOIN OLD_DASH D ON (Q.DASH_ID = D.ID)
SET Q.DASH_UUID = D.UUID;

-- idempotent
CALL core_drop_column_v1(
    'OLD_QUERY',
    'DASH_ID');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
