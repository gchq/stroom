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

-- Records that a processor task has written to an index shard, so that the task is not complete until
-- the shard has committed what it wrote, and so that a run of the task that is replaced can have what it
-- wrote removed. There is one row for each task and shard. Rows exist only for tasks that are not yet
-- finished with.
--
-- run is the task's run_count when the row was last written to.
-- writer_id is the opening of the shard that holds the run's documents (see index_shard.writer_id).
--
-- state is one of:
--   1 = writing    the run is still adding documents to the shard
--   2 = written    the run has ended, and the shard has not committed since
--   3 = committed  the shard has committed everything the run gave it
CREATE TABLE IF NOT EXISTS index_shard_task (
    task_id         bigint NOT NULL,
    index_shard_id  bigint NOT NULL,
    stream_id       bigint NOT NULL,
    run             int NOT NULL,
    writer_id       varchar(255) NOT NULL,
    state           tinyint NOT NULL,
    update_time_ms  bigint NOT NULL,
    PRIMARY KEY (task_id, index_shard_id),
    KEY index_shard_task_shard_writer (index_shard_id, writer_id, state),
    KEY index_shard_task_shard_stream (index_shard_id, stream_id),
    KEY index_shard_task_state_update_time (state, update_time_ms),
    CONSTRAINT index_shard_task_fk_index_shard_id
        FOREIGN KEY (index_shard_id) REFERENCES index_shard (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Records that the documents of a stream must be deleted from an index shard, whichever task indexed
-- them. There is one row for each shard and stream. The row goes once the deletion has been committed.
--
-- writer_id is null while the deletion is waiting. It is set to the opening of the shard that has
-- deleted the documents but not yet committed, and cleared again if that writer is lost or the
-- deletion is asked for again.
-- version goes up with every change, so that a commit removes only the rows it committed, and not one
-- that was asked for and deleted again while it ran.
CREATE TABLE IF NOT EXISTS index_shard_purge (
    index_shard_id  bigint NOT NULL,
    stream_id       bigint NOT NULL,
    writer_id       varchar(255) DEFAULT NULL,
    version         int NOT NULL,
    create_time_ms  bigint NOT NULL,
    PRIMARY KEY (index_shard_id, stream_id),
    KEY index_shard_purge_create_time (create_time_ms),
    CONSTRAINT index_shard_purge_fk_index_shard_id
        FOREIGN KEY (index_shard_id) REFERENCES index_shard (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS V08_00_00_005;

DELIMITER $$

CREATE PROCEDURE V08_00_00_005 ()
BEGIN
    DECLARE object_count integer;

    -- Each time a shard is opened for writing it gets a new writer id, which is cleared when the shard is
    -- closed, taken over or reset after a restart. Null when nothing is writing to the shard.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'index_shard'
    AND column_name = 'writer_id';

    IF object_count = 0 THEN
        ALTER TABLE index_shard ADD COLUMN writer_id varchar(255) DEFAULT NULL;
    END IF;

END $$

DELIMITER ;

CALL V08_00_00_005;

DROP PROCEDURE IF EXISTS V08_00_00_005;

-- Every node is stopped to upgrade, so nothing is writing to any shard. v7.14-beta.19 and v8.0-beta.1 may
-- have left leases, which nothing would ever release if the job that takes lapsed leases were disabled,
-- and shards left open by nodes that no longer exist, which no node would reset. Either would stop the
-- shard being written to or deleted for good. The writers that left them open may have been keeping
-- commits they had replaced, for readers on other nodes, so those are kept for a while yet.
UPDATE index_shard
SET status = 0,
    prune_commits_after_ms = GREATEST(
        COALESCE(prune_commits_after_ms, 0),
        CAST(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', UTC_TIMESTAMP(3)) DIV 1000 AS SIGNED) + 1800000)
WHERE status IN (1, 10, 20);

UPDATE index_shard
SET lease_instance_id = NULL,
    lease_expiry_ms = NULL
WHERE lease_instance_id IS NOT NULL
OR lease_expiry_ms IS NOT NULL;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
