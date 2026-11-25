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

DROP PROCEDURE IF EXISTS processor_task_lifecycle_add_column;
DROP PROCEDURE IF EXISTS processor_task_lifecycle_update_tasks;

DELIMITER $$

CREATE PROCEDURE processor_task_lifecycle_add_column (
    p_column_name varchar(64),
    p_column_definition varchar(255)
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_task'
    AND column_name = p_column_name;

    IF object_count = 0 THEN
        SET @sqlstmt = CONCAT(
            'ALTER TABLE processor_task ADD COLUMN ', p_column_name, ' ', p_column_definition);
        PREPARE stmt FROM @sqlstmt;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

-- Walks the table by id, committing each batch, so that a table with a very large number of tasks does not need one
-- very large transaction. Can be run again if it is interrupted, as it only changes rows it has not yet changed.
CREATE PROCEDURE processor_task_lifecycle_update_tasks (
    p_batch_size integer
)
BEGIN
    DECLARE v_now_ms bigint DEFAULT FLOOR(UNIX_TIMESTAMP(NOW(3)) * 1000);
    DECLARE v_last_id bigint DEFAULT 0;
    DECLARE v_max_id bigint;

    batches: LOOP
        SELECT MAX(id)
        INTO v_max_id
        FROM (
            SELECT id
            FROM processor_task
            WHERE id > v_last_id
            ORDER BY id
            LIMIT p_batch_size) batch;

        IF v_max_id IS NULL THEN
            LEAVE batches;
        END IF;

        -- FAILED (22) is no longer used, so these become ABORTED (23). Nothing runs them again. The status time is set
        -- so that they are kept for the whole of stroom.processor.abortedTaskDeleteAge from now.
        UPDATE processor_task
        SET status = 23,
            status_time_ms = v_now_ms,
            reason = 'Failed before upgrade',
            version = version + 1
        WHERE id > v_last_id
        AND id <= v_max_id
        AND status = 22;

        -- Unfinished tasks, i.e. CREATED (0), QUEUED (1), ASSIGNED (2) and PROCESSING (3), may have been run before, so
        -- they are treated as if they have been.
        UPDATE processor_task
        SET run_count = 1
        WHERE id > v_last_id
        AND id <= v_max_id
        AND status IN (0, 1, 2, 3)
        AND run_count = 0;

        COMMIT;

        SET v_last_id = v_max_id;
    END LOOP;
END $$

DELIMITER ;

-- Which run of the task this is. Goes up in the same update that sets the task to PROCESSING.
CALL processor_task_lifecycle_add_column("run_count", "int NOT NULL DEFAULT 0");

-- How many faults that count have been hit by runs of the task.
CALL processor_task_lifecycle_add_column("fault_count", "int NOT NULL DEFAULT 0");

-- Why the task has the status it has, if there is something to say. Set with the status.
CALL processor_task_lifecycle_add_column("reason", "varchar(1000) DEFAULT NULL");

CALL processor_task_lifecycle_update_tasks(10000);

DROP PROCEDURE IF EXISTS processor_task_lifecycle_add_column;
DROP PROCEDURE IF EXISTS processor_task_lifecycle_update_tasks;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
