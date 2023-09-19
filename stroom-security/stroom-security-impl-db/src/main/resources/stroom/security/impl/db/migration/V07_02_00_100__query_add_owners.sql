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

DROP PROCEDURE IF EXISTS V07_02_00_100;

DELIMITER $$

CREATE PROCEDURE V07_02_00_100 ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'query'
    AND column_name = 'owner_uuid';

    IF object_count = 1 THEN
        SELECT COUNT(1)
        INTO object_count
        FROM information_schema.tables
        WHERE table_schema = database()
        AND table_name = 'stroom_user';

        IF object_count = 1 THEN
            UPDATE query q, stroom_user s
            SET q.owner_uuid = s.uuid
            WHERE q.create_user = s.name;
        END IF;

    END IF;
    
END $$

DELIMITER ;

CALL V07_02_00_100;

DROP PROCEDURE IF EXISTS V07_02_00_100;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
