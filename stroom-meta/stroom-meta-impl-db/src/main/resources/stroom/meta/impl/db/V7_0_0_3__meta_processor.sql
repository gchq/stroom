--
-- Create the meta_processor table
--
CREATE TABLE IF NOT EXISTS meta_processor (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  processor_uuid 	    varchar(255) DEFAULT NULL,
  processor_filter_uuid varchar(255) DEFAULT NULL,
  pipeline_uuid 	    varchar(255) DEFAULT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            processor_uuid (processor_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy meta into the meta_processor table
--
DROP PROCEDURE IF EXISTS copy_meta_processor;
DELIMITER //
CREATE PROCEDURE copy_meta_processor ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_PROC' > 0) THEN
    INSERT
    INTO meta_processor (id, processor_uuid, pipeline_uuid)
    SELECT ID, ID, PIPE_UUID
    FROM STRM_PROC
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_processor)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE meta_processor AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM meta_processor;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_meta_processor();
DROP PROCEDURE copy_meta_processor;
