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

DROP PROCEDURE IF EXISTS V07_06_00_305__processor_filter_run_as_user_uuid;

DELIMITER $$

CREATE PROCEDURE V07_06_00_305__processor_filter_run_as_user_uuid ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_filter'
    AND column_name = 'run_as_user_uuid';

    IF object_count = 0 THEN
        ALTER TABLE processor_filter ADD COLUMN run_as_user_uuid varchar(255) DEFAULT NULL;

        SELECT COUNT(1)
        INTO object_count
        FROM information_schema.tables
        WHERE table_schema = database()
        AND table_name = 'doc_permission';

        IF object_count = 1 THEN
            SET @sql_str = CONCAT(
                'UPDATE processor_filter pf ',
                'INNER JOIN ( ',
                '    SELECT DISTINCT ',
                '        dp.doc_uuid, ',
                '        FIRST_VALUE(dp.user_uuid) '
                '            OVER (PARTITION BY dp.doc_uuid ORDER BY dp.id DESC) latest_owner_uuid ',
                '    FROM doc_permission dp ',
                '    WHERE dp.permission = "Owner" ',
                ') as dpv on dpv.doc_uuid = pf.uuid ',
                'SET pf.run_as_user_uuid = dpv.latest_owner_uuid; ');
            PREPARE stmt FROM @sql_str;
            EXECUTE stmt;
        END IF;

    END IF;
END $$

DELIMITER ;

CALL V07_06_00_305__processor_filter_run_as_user_uuid;

DROP PROCEDURE IF EXISTS V07_06_00_305__processor_filter_run_as_user_uuid;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
