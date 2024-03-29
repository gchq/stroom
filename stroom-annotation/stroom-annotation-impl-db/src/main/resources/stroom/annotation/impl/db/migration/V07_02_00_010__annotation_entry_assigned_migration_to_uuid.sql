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

DROP PROCEDURE IF EXISTS V07_02_00_010;

DELIMITER $$

CREATE PROCEDURE V07_02_00_010 ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.tables
    WHERE table_schema = database()
    AND table_name = 'stroom_user';

    IF object_count = 1 THEN
        SET @sql_str = CONCAT(
            'UPDATE ',
            '  annotation_entry ae, ',
            '  stroom_user s ',
            'SET ae.data = s.uuid ',
            'WHERE ae.data = s.name ',
            'AND ae.type = \'Assigned\'');
        PREPARE stmt FROM @sql_str;
        EXECUTE stmt;
    END IF;
END $$

DELIMITER ;

CALL V07_02_00_010;

DROP PROCEDURE IF EXISTS V07_02_00_010;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
