--
-- Create the meta_processor table
--
-- processor_id comes from the processor table in stroom-process but there is no FK
-- between them
CREATE TABLE IF NOT EXISTS meta_processor (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  pipeline_uuid 	    varchar(255) NOT NULL,
  processor_id   	    int(11) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            processor_id (processor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the meta_processor table
--
DROP PROCEDURE IF EXISTS copy_meta_processor;
DELIMITER //
CREATE PROCEDURE copy_meta_processor ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_PROC' > 0) THEN
    -- Copy data into the meta_processor table, use ID predicate to make it re-runnable
    INSERT
    INTO meta_processor (id, pipeline_uuid, processor_id)
    SELECT SP.ID, P.UUID, SP.ID
    FROM STRM_PROC SP
    INNER JOIN PIPE P ON (P.ID = SP.FK_PIPE_ID)
    WHERE SP.ID > (SELECT COALESCE(MAX(mp.id), 0) FROM meta_processor mp)
    ORDER BY SP.ID;

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
