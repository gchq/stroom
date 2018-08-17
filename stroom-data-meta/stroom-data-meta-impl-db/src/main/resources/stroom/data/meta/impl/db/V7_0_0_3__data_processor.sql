--
-- Create the data_processor table
--
CREATE TABLE IF NOT EXISTS data_processor (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  pipeline_uuid 	    varchar(255) NOT NULL,
  processor_id   	    int(11) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            processor_id (processor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the data_processor table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_PROC' > 0) THEN
    INSERT
    INTO data_processor (id, pipeline_uuid, processor_id)
    SELECT ID, PIPE_UUID, ID
    FROM STRM_PROC
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM data_processor)
    ORDER BY ID;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_STRM_PROC' > 0) THEN
    INSERT INTO data_processor (id, pipeline_uuid, processor_id)
    SELECT ID, PIPE_UUID, ID
    FROM OLD_STRM_PROC
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM data_processor)
    ORDER BY ID;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
