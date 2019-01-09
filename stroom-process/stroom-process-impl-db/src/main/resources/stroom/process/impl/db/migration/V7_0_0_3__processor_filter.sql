
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

CREATE TABLE IF NOT EXISTS processor_filter (
  id int(11) NOT NULL AUTO_INCREMENT,
  version tinyint(4) NOT NULL,
  create_time_ms bigint(20) DEFAULT NULL,
  create_user varchar(255) DEFAULT NULL,
  update_time_ms bigint(20) DEFAULT NULL,
  update_user varchar(255) DEFAULT NULL,
  data longtext NOT NULL,
  priority int(11) NOT NULL,
  fk_processor_id int(11) NOT NULL,
  fk_processor_filter_tracker_id int(11) NOT NULL,
  enabled bit(1) NOT NULL,
  PRIMARY KEY (id),
  KEY processor_filter_fk_processor_id (fk_processor_id),
  KEY processor_filter_fk_processor_filter_tracker_id (fk_processor_filter_tracker_id),
  CONSTRAINT processor_filter_fk_processor_filter_tracker_id FOREIGN KEY (fk_processor_filter_tracker_id) REFERENCES processor_filter_tracker (id),
  CONSTRAINT processor_filter_fk_processor_id FOREIGN KEY (fk_processor_id) REFERENCES processor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_PROC_FILT' > 0) THEN
        --
        -- Copy data into the table, use ID predicate to make it re-runnable
        --
        INSERT
        INTO processor_filter (id, version, create_time_ms, create_user, update_time_ms, update_user, data, priority, fk_processor_id, fk_processor_filter_tracker_id, enabled)
        SELECT ID, VER, CRT_MS, CRT_USER, UPD_MS, UPD_USER, DAT, PRIOR, FK_STRM_PROC_ID, FK_STRM_PROC_FILT_TRAC_ID, ENBL
        FROM STRM_PROC_FILT
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor_filter AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor_filter;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
