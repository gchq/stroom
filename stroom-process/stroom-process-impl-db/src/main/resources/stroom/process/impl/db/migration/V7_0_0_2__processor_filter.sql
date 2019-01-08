--Create Table: CREATE TABLE `STRM_PROC` (
--  `ID` int(11) NOT NULL AUTO_INCREMENT,
--  `VER` tinyint(4) NOT NULL,
--  `CRT_USER` varchar(255) DEFAULT NULL,
--  `UPD_USER` varchar(255) DEFAULT NULL,
--  `TASK_TP` varchar(255) DEFAULT NULL,
--  `FK_PIPE_ID` int(11) DEFAULT NULL,
--  `ENBL` bit(1) NOT NULL,
--  `CRT_MS` bigint(20) DEFAULT NULL,
--  `UPD_MS` bigint(20) DEFAULT NULL,
--  PRIMARY KEY (`ID`),
--  KEY `STRM_PROC_FK_PIPE_ID` (`FK_PIPE_ID`),
--  CONSTRAINT `STRM_PROC_FK_PIPE_ID` FOREIGN KEY (`FK_PIPE_ID`) REFERENCES `PIPE` (`ID`)
--) ENGINE=InnoDB DEFAULT CHARSET=latin1

--Create Table: CREATE TABLE `STRM_PROC_FILT` (
--  `ID` int(11) NOT NULL AUTO_INCREMENT,
--  `VER` tinyint(4) NOT NULL,
--  `CRT_MS` bigint(20) DEFAULT NULL,
--  `CRT_USER` varchar(255) DEFAULT NULL,
--  `UPD_MS` bigint(20) DEFAULT NULL,
--  `UPD_USER` varchar(255) DEFAULT NULL,
--  `DAT` longtext NOT NULL,
--  `PRIOR` int(11) NOT NULL,
--  `FK_STRM_PROC_ID` int(11) NOT NULL,
--  `FK_STRM_PROC_FILT_TRAC_ID` int(11) NOT NULL,
--  `ENBL` bit(1) NOT NULL,
--  PRIMARY KEY (`ID`),
--  KEY `STRM_PROC_FILT_FK_STRM_PROC_ID` (`FK_STRM_PROC_ID`),
--  KEY `STRM_PROC_FILT_FK_STRM_PROC_FILT_TRAC_ID` (`FK_STRM_PROC_FILT_TRAC_ID`),
--  CONSTRAINT `STRM_PROC_FILT_FK_STRM_PROC_FILT_TRAC_ID` FOREIGN KEY (`FK_STRM_PROC_FILT_TRAC_ID`) REFERENCES `STRM_PROC_FILT_TRAC` (`ID`),
--  CONSTRAINT `STRM_PROC_FILT_FK_STRM_PROC_ID` FOREIGN KEY (`FK_STRM_PROC_ID`) REFERENCES `STRM_PROC` (`ID`)
--) ENGINE=InnoDB DEFAULT CHARSET=utf8

--
-- Create the table
--

CREATE TABLE processor_filter (
  id int(11) NOT NULL AUTO_INCREMENT,
  ver tinyint(4) NOT NULL,
  crt_ms bigint(20) DEFAULT NULL,
  crt_user varchar(255) DEFAULT NULL,
  upd_ms bigint(20) DEFAULT NULL,
  upd_user varchar(255) DEFAULT NULL,
  dat longtext NOT NULL,
  prior int(11) NOT NULL,
  fk_processor_id int(11) NOT NULL,
  fk_processor_filter_tracker_id int(11) NOT NULL,
  enbl bit(1) NOT NULL,
  PRIMARY KEY (id),
  KEY processor_filter_fk_processor_id (fk_processor_id),
  KEY processor_filter_fk_processor_filter_tracker_id (fk_processor_filter_tracker_id),
  CONSTRAINT processor_filter_fk_processor_filter_tracker_id FOREIGN KEY (fk_processor_filter_tracker_id) REFERENCES processor_filter_tracker (id),
  CONSTRAINT processor_filter_fk_processor_id FOREIGN KEY (fk_processor_id) REFERENCES processor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


--
-- Copy data into the table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_PROC_FILT' > 0) THEN
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
