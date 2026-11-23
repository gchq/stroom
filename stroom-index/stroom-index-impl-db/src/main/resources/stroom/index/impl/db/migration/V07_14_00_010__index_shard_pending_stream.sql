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

-- Records each stream that has documents in an index shard that are not yet known to be committed, so
-- that when a task or a commit fails the stream's documents can be removed and the stream indexed again.
-- There is a row for each shard the stream's task has written to. A stream that is being indexed again
-- also has one row with an index_shard_id of 0, which holds the number of attempts made.
--
-- state is one of:
--   0 = writing        the task is adding the stream's documents to the shard
--   1 = written        the task has finished but the shard has not committed since
--   2 = committed      the shard has committed the documents; the row goes once all the stream's rows are so
--   3 = abandoned      the documents are to be removed, once the task that is still writing them has stopped
--   4 = delete         the documents are to be removed from the shard
--   5 = retry pending  (attempt row) the task is to be run again once the documents have been removed
--   6 = retrying       (attempt row) the task has been put back to run again
CREATE TABLE IF NOT EXISTS index_shard_pending_stream (
    id              bigint NOT NULL AUTO_INCREMENT,
    index_shard_id  bigint NOT NULL,
    stream_id       bigint NOT NULL,
    task_id         bigint NOT NULL,
    attempt         int NOT NULL DEFAULT 0,
    state           tinyint NOT NULL,
    update_time_ms  bigint NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY index_shard_pending_stream_shard_stream_task (index_shard_id, stream_id, task_id),
    KEY index_shard_pending_stream_stream_task (stream_id, task_id),
    KEY index_shard_pending_stream_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS V07_14_00_010;

DELIMITER $$

CREATE PROCEDURE V07_14_00_010 ()
BEGIN
    DECLARE object_count integer;

    -- Set when a shard stops being written to without a final commit, e.g. because its node died or lost
    -- the lease. The streams it then holds pending rows for may have lost documents, so whichever node
    -- next opens the shard marks them to be removed and indexed again before it clears this.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'index_shard'
    AND column_name = 'recover_pending_streams';

    IF object_count = 0 THEN
        ALTER TABLE index_shard ADD COLUMN recover_pending_streams tinyint NOT NULL DEFAULT 0;
    END IF;

END $$

DELIMITER ;

CALL V07_14_00_010;

DROP PROCEDURE IF EXISTS V07_14_00_010;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
