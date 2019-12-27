-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the meta table
--
CREATE TABLE IF NOT EXISTS meta (
  id 				bigint(20) NOT NULL AUTO_INCREMENT,
  create_time       bigint(20) NOT NULL,
  effective_time	bigint(20) DEFAULT NULL,
  parent_id     	bigint(20) DEFAULT NULL,
  status			tinyint(4) NOT NULL,
  status_time       bigint(20) DEFAULT NULL,
  feed_id	    	int(11) NOT NULL,
  type_id           int(11) NOT NULL,
  processor_id    	int(11) DEFAULT NULL,
  PRIMARY KEY       (id),
  CONSTRAINT meta_feed_id FOREIGN KEY (feed_id) REFERENCES meta_feed (id),
  CONSTRAINT meta_type_id FOREIGN KEY (type_id) REFERENCES meta_type (id),
  CONSTRAINT meta_processor_id FOREIGN KEY (processor_id) REFERENCES meta_processor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


--
-- Copy meta into the meta table
--
DROP PROCEDURE IF EXISTS copy_meta;
DELIMITER //
CREATE PROCEDURE copy_meta ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM' > 0) THEN
    INSERT INTO meta (id, create_time, effective_time, parent_id, status, status_time, feed_id, type_id, processor_id)
    SELECT ID, CRT_MS, EFFECT_MS, PARNT_STRM_ID, STAT, STAT_MS, FK_FD_ID, FK_STRM_TP_ID, FK_STRM_PROC_ID
    FROM STRM
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE meta AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM meta;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_meta();
DROP PROCEDURE copy_meta;

SET SQL_NOTES=@SQL_NOTES;
