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

-- Create the table
CREATE TABLE IF NOT EXISTS processor (
  id int(11) NOT NULL AUTO_INCREMENT,
  version tinyint(4) NOT NULL,
  create_user varchar(255) DEFAULT NULL,
  update_user varchar(255) DEFAULT NULL,
  task_type varchar(255) DEFAULT NULL,
  pipeline_uuid varchar(255) NOT NULL,
  enabled bit(1) NOT NULL,
  create_time bigint(20) DEFAULT NULL,
  update_time bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY strm_proc_fk_pipe_id (fk_pipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8

--
-- Copy data into the data_processor table, use ID predicate to make it re-runnable
--
INSERT
INTO processor (id, version, create_user, update_user, task_type, pipeline_uuid, enabled, create_time, update_time)
SELECT SP.ID, SP.VER, SP.CRT_USER, SP.UPD_USER, SP.TASK_TP, P.UUID, SP.ENBL, SP.CRT_MS, SP.UPD_MS
FROM STRM_PROC SP
INNER JOIN PIPE P ON (P.ID = SP.FK_PIPE_ID)
WHERE SP.ID > (SELECT COALESCE(MAX(id), 0) FROM data_processor)
ORDER BY SP.ID;

-- Work out what to set our auto_increment start value to
SELECT COALESCE(MAX(id) + 1, 1)
INTO @next_id
FROM data_processor

ALTER TABLE data_processor AUTO_INCREMENT=@next_id;


