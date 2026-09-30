-- ------------------------------------------------------------------------
-- Copyright 2025 Crown Copyright
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

DROP PROCEDURE IF EXISTS V07_14_00_001__processor_filter_tracker_prev_max_meta_id;

DELIMITER $$

CREATE PROCEDURE V07_14_00_001__processor_filter_tracker_prev_max_meta_id ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_filter_tracker'
    AND column_name = 'prev_max_meta_id';

    IF object_count = 0 THEN
        -- The max meta id seen on the previous task creation poll. Task creation is bounded
        -- by this rather than the live max meta id so that a meta row that was inserted but
        -- not yet committed when the max was read has a full poll interval to become visible
        -- before the tracker moves past it. Null means no poll has established a value yet.
        ALTER TABLE processor_filter_tracker ADD COLUMN prev_max_meta_id bigint DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_001__processor_filter_tracker_prev_max_meta_id;

DROP PROCEDURE IF EXISTS V07_14_00_001__processor_filter_tracker_prev_max_meta_id;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
