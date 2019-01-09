--Create Table: CREATE TABLE `STRM_TASK` (
--  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
--  `VER` tinyint(4) NOT NULL,
--  `CRT_MS` bigint(20) DEFAULT NULL,
--  `END_TIME_MS` bigint(20) DEFAULT NULL,
--  `STAT` tinyint(4) NOT NULL,
--  `STAT_MS` bigint(20) DEFAULT NULL,
--  `START_TIME_MS` bigint(20) DEFAULT NULL,
--  `FK_ND_ID` int(11) DEFAULT NULL,
--  `FK_STRM_ID` bigint(20) NOT NULL,
--  `DAT` longtext,
--  `FK_STRM_PROC_FILT_ID` int(11) DEFAULT NULL,
--  PRIMARY KEY (`ID`),
--  KEY `STRM_TASK_FK_ND_ID` (`FK_ND_ID`),
--  KEY `STRM_TASK_STAT_IDX` (`STAT`),
--  KEY `STRM_TASK_FK_STRM_ID` (`FK_STRM_ID`),
--  KEY `STRM_TASK_FK_STRM_PROC_FILT_ID` (`FK_STRM_PROC_FILT_ID`),
--  CONSTRAINT `STRM_TASK_FK_ND_ID` FOREIGN KEY (`FK_ND_ID`) REFERENCES `ND` (`ID`),
--  CONSTRAINT `STRM_TASK_FK_STRM_PROC_FILT_ID` FOREIGN KEY (`FK_STRM_PROC_FILT_ID`) REFERENCES `STRM_PROC_FILT` (`ID`)
--) ENGINE=InnoDB DEFAULT CHARSET=latin1

--
-- Create the table
--

CREATE TABLE processor_filter_task (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  version tinyint(4) NOT NULL,
  create_time_ms bigint(20) DEFAULT NULL,
  end_time_ms bigint(20) DEFAULT NULL,
  status tinyint(4) NOT NULL,
  status_time_ms bigint(20) DEFAULT NULL,
  start_time_ms bigint(20) DEFAULT NULL,
  node_name varchar(255) DEFAULT NULL,
  stream_id bigint(20) NOT NULL,
  data longtext,
  fk_processor_filter_id int(11) NOT NULL,
  PRIMARY KEY (id),
  KEY processor_filter_task_status_idx (stat),
  KEY processor_filter_task_stream_id_idx (stream_id),
  KEY processor_filter_task_fk_processor_filter_id (fk_processor_filter_id),
  KEY processor_filter_task_node_name_idx (node_name),
  CONSTRAINT processor_filter_task_fk_processor_filter_id FOREIGN KEY (fk_processor_filter_id) REFERENCES processor_filter (id)
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
        INTO processor_filter_task (id, version, create_time_ms, end_time_ms, status, status_time_ms, start_time_ms, node_name, stream_id, data, fk_processor_filter_id)
        SELECT ID, VER, CRT_MS, END_TIME_MS, STAT, STAT_MS, START_TIME_MS, FK_ND_ID, FK_STRM_ID, DAT, FK_STRM_PROC_FILT_ID
        FROM STRM_TASK
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor_filter_task)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor_filter_task AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor_filter_task;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;

