-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
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

CREATE TABLE IF NOT EXISTS processor_task (
    id                        bigint(20) NOT NULL AUTO_INCREMENT,
    version                   int(11) NOT NULL,
    fk_processor_filter_id    int(11) NOT NULL,
    fk_processor_node_id      int(11) DEFAULT NULL,
    fk_processor_feed_id      int(11) DEFAULT NULL,
    create_time_ms            bigint(20) DEFAULT NULL,
    start_time_ms             bigint(20) DEFAULT NULL,
    end_time_ms               bigint(20) DEFAULT NULL,
    status                    tinyint(4) NOT NULL,
    status_time_ms            bigint(20) DEFAULT NULL,
    meta_id                   bigint(20) NOT NULL,
    data                      longtext,
    PRIMARY KEY               (id),
    KEY processor_task_fk_processor_filter_id (fk_processor_filter_id),
    KEY processor_task_fk_processor_node_id (fk_processor_node_id),
    KEY processor_task_fk_processor_feed_id (fk_processor_feed_id),
    KEY processor_task_status_idx (status),
    KEY processor_task_meta_id_idx (meta_id),
    CONSTRAINT processor_task_fk_processor_filter_id 
        FOREIGN KEY (fk_processor_filter_id)
        REFERENCES processor_filter (id),
    CONSTRAINT processor_task_fk_processor_node_id
        FOREIGN KEY (fk_processor_node_id)
        REFERENCES processor_node (id),
    CONSTRAINT processor_task_fk_processor_feed_id
        FOREIGN KEY (fk_processor_feed_id)
        REFERENCES processor_feed (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS copy_processor_task;
DELIMITER //
CREATE PROCEDURE copy_processor_task ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'STRM_TASK') THEN

        RENAME TABLE STRM_TASK TO OLD_STRM_TASK;

    END IF;
    -- Check again so it is idempotent
    IF EXISTS (
        SELECT NULL 
        FROM INFORMATION_SCHEMA.TABLES 
        WHERE TABLE_NAME = 'OLD_STRM_TASK') THEN
        -- Copy data into the table, use ID predicate to make it re-runnable
        INSERT
        INTO processor_task (
            id, 
            version, 
            fk_processor_filter_id, 
            fk_processor_node_id, 
            create_time_ms, 
            start_time_ms, 
            end_time_ms, 
            status, 
            status_time_ms, 
            meta_id, 
            data)
        SELECT 
            ID, 
            VER, 
            FK_STRM_PROC_FILT_ID, 
            FK_ND_ID, 
            CRT_MS,
            START_TIME_MS,
            END_TIME_MS,
            STAT,
            STAT_MS, 
            FK_STRM_ID, 
            DAT
        FROM OLD_STRM_TASK
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor_task)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor_task AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor_task;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;

END//
DELIMITER ;

CALL copy_processor_task();

DROP PROCEDURE copy_processor_task;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
