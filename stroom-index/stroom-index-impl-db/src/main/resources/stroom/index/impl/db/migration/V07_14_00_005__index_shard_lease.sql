/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_14_00_005;

DELIMITER $$

CREATE PROCEDURE V07_14_00_005 ()
BEGIN
    DECLARE object_count integer;

    -- A node must hold the lease on a shard on a shared index volume to write to it. A leased shard records
    -- the instance id of the process that holds the lease and when the lease expires, in database time. The
    -- holder's node name is in node_name. Both lease columns are null when the shard is not leased, which
    -- is the case for every existing shard and for all shards on local index volumes.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'index_shard'
    AND column_name = 'lease_instance_id';

    IF object_count = 0 THEN
        ALTER TABLE index_shard
            ADD COLUMN lease_instance_id varchar(255) DEFAULT NULL,
            ADD COLUMN lease_expiry_ms bigint DEFAULT NULL;
    END IF;

    -- The time after which the commits a writer had replaced, but was still keeping for readers on other
    -- nodes when it closed, can be removed from the shard. Null when there are none to remove.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'index_shard'
    AND column_name = 'prune_commits_after_ms';

    IF object_count = 0 THEN
        ALTER TABLE index_shard ADD COLUMN prune_commits_after_ms bigint DEFAULT NULL;
    END IF;

    -- Renewing leases finds them by the instance that holds them.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = 'index_shard'
    AND index_name = 'index_shard_lease_instance_id';

    IF object_count = 0 THEN
        CREATE INDEX index_shard_lease_instance_id ON index_shard (lease_instance_id);
    END IF;

END $$

DELIMITER ;

CALL V07_14_00_005;

DROP PROCEDURE IF EXISTS V07_14_00_005;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
