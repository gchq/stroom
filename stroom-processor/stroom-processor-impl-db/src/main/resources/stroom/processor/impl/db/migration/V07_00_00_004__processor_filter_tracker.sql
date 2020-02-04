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

--Create Table: CREATE TABLE `STRM_PROC_FILT_TRAC` (
--  `ID` int(11) NOT NULL AUTO_INCREMENT,
--  `VER` tinyint(4) NOT NULL,
--  `MIN_STRM_ID` bigint(20) NOT NULL,
--  `MIN_EVT_ID` bigint(20) NOT NULL,
--  `MIN_STRM_CRT_MS` bigint(20) DEFAULT NULL,
--  `MAX_STRM_CRT_MS` bigint(20) DEFAULT NULL,
--  `STRM_CRT_MS` bigint(20) DEFAULT NULL,
--  `LAST_POLL_MS` bigint(20) DEFAULT NULL,
--  `LAST_POLL_TASK_CT` int(11) DEFAULT NULL,
--  `STAT` varchar(255) DEFAULT NULL,
--  `STRM_CT` bigint(20) DEFAULT NULL,
--  `EVT_CT` bigint(20) DEFAULT NULL,
--  PRIMARY KEY (`ID`)
--) ENGINE=InnoDB DEFAULT CHARSET=latin1

-- Create the table
CREATE TABLE IF NOT EXISTS processor_filter_tracker (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  min_meta_id           bigint(20) NOT NULL,
  min_event_id          bigint(20) NOT NULL,
  min_meta_create_ms    bigint(20) DEFAULT NULL,
  max_meta_create_ms    bigint(20) DEFAULT NULL,
  meta_create_ms        bigint(20) DEFAULT NULL,
  last_poll_ms          bigint(20) DEFAULT NULL,
  last_poll_task_count  int(11) DEFAULT NULL,
  status                varchar(255) DEFAULT NULL,
  meta_count            bigint(20) DEFAULT NULL,
  event_count           bigint(20) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS copy_processor_filter_tracker;
DELIMITER //
CREATE PROCEDURE copy_processor_filter_tracker ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_TASK' > 0) THEN
        -- Copy data into the table, use ID predicate to make it re-runnable
        INSERT
        INTO processor_filter_tracker (
            id,
            version,
            min_meta_id,
            min_event_id,
            min_meta_create_ms,
            max_meta_create_ms,
            meta_create_ms,
            last_poll_ms,
            last_poll_task_count,
            status,
            meta_count,
            event_count)
        SELECT
            ID,
            VER,
            MIN_STRM_ID,
            MIN_EVT_ID,
            MIN_STRM_CRT_MS,
            MAX_STRM_CRT_MS,
            STRM_CRT_MS,
            LAST_POLL_MS,
            LAST_POLL_TASK_CT,
            STAT,
            STRM_CT,
            EVT_CT
        FROM STRM_PROC_FILT_TRAC
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor_filter_tracker)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor_filter_tracker AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor_filter_tracker;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy_processor_filter_tracker();
DROP PROCEDURE copy_processor_filter_tracker;


SET SQL_NOTES=@OLD_SQL_NOTES;
