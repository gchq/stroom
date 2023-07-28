-- ------------------------------------------------------------------------
-- Copyright 2023 Crown Copyright
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
    AND table_name = 'annotation'
    AND column_name = 'assigned_to_uuid';

    IF object_count = 0 THEN

        ALTER TABLE annotation
        RENAME COLUMN assigned_to TO assigned_to_uuid;

        SELECT COUNT(1)
        INTO object_count
        FROM information_schema.tables
        WHERE table_schema = database()
        AND table_name = 'stroom_user';

        IF object_count = 1 THEN
            UPDATE annotation a, stroom_user s
            SET a.assigned_to_uuid = s.uuid
            WHERE a.assigned_to_uuid = s.name;
        END IF;

    END IF;
END $$

DELIMITER ;

CALL V07_02_00_005;

DROP PROCEDURE IF EXISTS V07_02_00_005;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
