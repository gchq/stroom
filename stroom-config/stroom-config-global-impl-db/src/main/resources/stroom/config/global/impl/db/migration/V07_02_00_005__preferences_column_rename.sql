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

DROP PROCEDURE IF EXISTS V07_02_00_005;

DELIMITER $$

CREATE PROCEDURE V07_02_00_005 ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'preferences'
    AND column_name = 'user_uuid';

    IF object_count = 0 THEN

        SELECT COUNT(1)
        INTO object_count
        FROM information_schema.tables
        WHERE table_schema = database()
        AND table_name = 'stroom_user';

        IF object_count = 1 THEN
            SET @sql_str = CONCAT(
                'UPDATE preferences p, stroom_user s ',
                'SET p.user_id = s.uuid ',
                'WHERE p.user_id = s.name');
            PREPARE stmt FROM @sql_str;
            EXECUTE stmt;
        END IF;

        ALTER TABLE preferences
        RENAME COLUMN user_id TO user_uuid;
    END IF;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.table_constraints
    WHERE table_schema = database()
    AND table_name = 'preferences'
    AND constraint_name = 'user_id';

    IF object_count = 1 THEN
        ALTER TABLE preferences DROP INDEX user_id;
    END IF;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.table_constraints
    WHERE table_schema = database()
    AND table_name = 'preferences'
    AND constraint_name = 'user_uuid';

    IF object_count = 0 THEN
        CREATE UNIQUE INDEX user_uuid ON preferences (user_uuid);
    END IF;
END $$

DELIMITER ;

CALL V07_02_00_005;

DROP PROCEDURE IF EXISTS V07_02_00_005;

SET SQL_NOTES=@OLD_SQL_NOTES;
