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
CREATE TABLE processor_filter_tracker (
  id int(11) NOT NULL AUTO_INCREMENT,
  version tinyint(4) NOT NULL,
  min_stream_id bigint(20) NOT NULL,
  min_event_id bigint(20) NOT NULL,
  min_stream_create_ms bigint(20) DEFAULT NULL,
  max_stream_create_ms bigint(20) DEFAULT NULL,
  stream_create_ms bigint(20) DEFAULT NULL,
  last_poll_ms bigint(20) DEFAULT NULL,
  last_poll_task_count int(11) DEFAULT NULL,
  status varchar(255) DEFAULT NULL,
  stream_count bigint(20) DEFAULT NULL,
  event_count bigint(20) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_TASK' > 0) THEN
        -- Copy data into the table, use ID predicate to make it re-runnable
        INSERT
        INTO processor_filter_tracker (
            id, version, min_stream_id, min_event_id, min_stream_create_ms, max_stream_create_ms,
            stream_create_ms, last_poll_ms, last_poll_task_count, status, stream_count, event_count)
        SELECT
            ID, VER, MIN_STRM_ID, MIN_EVT_ID, MIN_STRM_CRT_MS, MAX_STRM_CRT_MS,
            STRM_CRT_MS, LAST_POLL_MS, LAST_POLL_TASK_CT, STAT, STRM_CT, EVT_CT
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
CALL copy();
DROP PROCEDURE copy;

