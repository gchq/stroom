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

DROP PROCEDURE IF EXISTS V07_14_00_002__cluster_lock_more_columns;

DELIMITER $$

CREATE PROCEDURE V07_14_00_002__cluster_lock_more_columns ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'cluster_lock'
    AND column_name = 'node_name';

    IF object_count = 0 THEN
        -- The node holding the lock, written when the lock is taken. Reported to whoever is waiting
        -- on a contended lock. It does not decide who may extend or release the lock: lock_token does,
        -- because a node name is not unique to one acquisition.
        ALTER TABLE cluster_lock ADD COLUMN node_name varchar(255) DEFAULT NULL;
    END IF;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'cluster_lock'
    AND column_name = 'thread_name';

    IF object_count = 0 THEN
        -- The thread on that node holding the lock, reported alongside node_name when a lock is
        -- contended.
        ALTER TABLE cluster_lock ADD COLUMN thread_name varchar(255) DEFAULT NULL;
    END IF;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'cluster_lock'
    AND column_name = 'lease_ms';

    IF object_count = 0 THEN
        -- How long the lock may be held without the holder extending it. The lock becomes available to
        -- another node once lock_time_ms plus this has passed, so a node that dies does not hold it
        -- forever.
        ALTER TABLE cluster_lock ADD COLUMN lease_ms bigint DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_002__cluster_lock_more_columns;

DROP PROCEDURE IF EXISTS V07_14_00_002__cluster_lock_more_columns;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
