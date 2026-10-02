-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
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

DROP PROCEDURE IF EXISTS V07_14_00_001__cluster_lock_time;

DELIMITER $$

CREATE PROCEDURE V07_14_00_001__cluster_lock_time ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'cluster_lock'
    AND column_name = 'lock_time_ms';

    IF object_count = 0 THEN
        -- When the lock was taken, as epoch millis, refreshed each time the holder extends its lease
        -- and cleared when the lock is released. NULL means the lock is free.
        ALTER TABLE cluster_lock ADD COLUMN lock_time_ms bigint DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_001__cluster_lock_time;

DROP PROCEDURE IF EXISTS V07_14_00_001__cluster_lock_time;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
