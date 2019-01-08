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
  min_stream_id bigint(20) NOT NULL, -- TODO ????
  min_evt_id bigint(20) NOT NULL, -- TODO ????
  min_stream_create_ms bigint(20) DEFAULT NULL, -- TODO ????
  max_stream_create_ms bigint(20) DEFAULT NULL, -- TODO ????
  stream_create_ms bigint(20) DEFAULT NULL, -- TODO ????
  last_poll_ms bigint(20) DEFAULT NULL,
  last_poll_task_count int(11) DEFAULT NULL,
  status varchar(255) DEFAULT NULL,
  stream_count bigint(20) DEFAULT NULL, -- TODO ????
  evt_count bigint(20) DEFAULT NULL, -- TODO ????
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Copy data into the table, use ID predicate to make it re-runnable
INSERT
INTO processor_filter_tracker () -- TODO
SELECT  -- TODO
FROM STRM_PROC_FILT_TRAC
WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor_filter_tracker)
ORDER BY ID;

-- Work out what to set our auto_increment start value to
SELECT COALESCE(MAX(id) + 1, 1)
INTO @next_id
FROM processor_filter_tracker

ALTER TABLE processor_filter_tracker AUTO_INCREMENT=@next_id;

