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

DROP PROCEDURE IF EXISTS V07_14_00_003__cluster_lock_token;

DELIMITER $$

CREATE PROCEDURE V07_14_00_003__cluster_lock_token ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'cluster_lock'
    AND column_name = 'lock_token';

    IF object_count = 0 THEN
        -- Identifies one acquisition of the lock. Written when the lock is taken and matched when it is
        -- extended or released, so a holder whose lease has expired cannot extend or release the lock a
        -- later holder now owns. node_name and thread_name are not unique enough to do this: they are
        -- kept for reporting who holds a contended lock.
        ALTER TABLE cluster_lock ADD COLUMN lock_token varchar(36) DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_003__cluster_lock_token;

DROP PROCEDURE IF EXISTS V07_14_00_003__cluster_lock_token;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
