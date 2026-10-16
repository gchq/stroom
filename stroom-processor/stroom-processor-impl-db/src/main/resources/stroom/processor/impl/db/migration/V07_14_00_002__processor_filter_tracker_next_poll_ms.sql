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

DROP PROCEDURE IF EXISTS V07_14_00_002__processor_filter_tracker_next_poll_ms;

DELIMITER $$

CREATE PROCEDURE V07_14_00_002__processor_filter_tracker_next_poll_ms ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_filter_tracker'
    AND column_name = 'next_poll_ms';

    IF object_count = 0 THEN
        -- The earliest time that task creation should poll this filter again. It is only set
        -- when a poll creates no tasks, and each successive non producing poll pushes it
        -- further out, up to a maximum, so that filters with nothing to do are polled less
        -- often. Null means poll on the next task creation run.
        ALTER TABLE processor_filter_tracker ADD COLUMN next_poll_ms bigint DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_002__processor_filter_tracker_next_poll_ms;

DROP PROCEDURE IF EXISTS V07_14_00_002__processor_filter_tracker_next_poll_ms;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
