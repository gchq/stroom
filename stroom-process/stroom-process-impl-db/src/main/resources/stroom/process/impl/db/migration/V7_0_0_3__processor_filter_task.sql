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
  ver tinyint(4) NOT NULL,
  crt_ms bigint(20) DEFAULT NULL,
  end_time_ms bigint(20) DEFAULT NULL,
  stat tinyint(4) NOT NULL,
  stat_ms bigint(20) DEFAULT NULL,
  start_time_ms bigint(20) DEFAULT NULL,
  fk_nd_id int(11) DEFAULT NULL,
  fk_strm_id bigint(20) NOT NULL,
  dat longtext,
  fk_processor_filter_id int(11) NOT NULL,
  PRIMARY KEY (id),
  KEY strm_task_stat_idx (stat),
  KEY strm_task_fk_strm_id (fk_strm_id),
  KEY processor_filter_task_fk_processor_filter_id (fk_processor_filter_id),
  KEY processor_filter_task_fk_nd_id (fk_nd_id),
  CONSTRAINT processor_filter_task_fk_nd_id FOREIGN KEY (fk_nd_id) REFERENCES ND (id),
  CONSTRAINT processor_filter_task_fk_processor_filter_id FOREIGN KEY (fk_processor_filter_id) REFERENCES processor_filter (id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Copy data into the table
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
