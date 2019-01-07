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

--
-- Create the table
--

CREATE TABLE processor_filter_tracker (
  id int(11) NOT NULL AUTO_INCREMENT,
  ver tinyint(4) NOT NULL,
  min_strm_id bigint(20) NOT NULL,
  min_evt_id bigint(20) NOT NULL,
  min_strm_crt_ms bigint(20) DEFAULT NULL,
  max_strm_crt_ms bigint(20) DEFAULT NULL,
  strm_crt_ms bigint(20) DEFAULT NULL,
  last_poll_ms bigint(20) DEFAULT NULL,
  last_poll_task_ct int(11) DEFAULT NULL,
  stat varchar(255) DEFAULT NULL,
  strm_ct bigint(20) DEFAULT NULL,
  evt_ct bigint(20) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Copy data into the config table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN
        INSERT INTO config (name, val)
        SELECT NAME, VAL
        FROM GLOB_PROP
        ORDER BY NAME;

        INSERT INTO config_history (update_time, update_user, name, val)
        SELECT UPD_MS, UPD_USER, NAME, VAL
        FROM GLOB_PROP
        ORDER BY NAME;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_GLOB_PROP' > 0) THEN
      INSERT INTO config (name, val)
      SELECT NAME, VAL
      FROM OLD_GLOB_PROP
      ORDER BY NAME;

      INSERT INTO config_history (update_time, update_user, name, val)
      SELECT UPD_MS, UPD_USER, NAME, VAL
      FROM OLD_GLOB_PROP
      ORDER BY NAME;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
