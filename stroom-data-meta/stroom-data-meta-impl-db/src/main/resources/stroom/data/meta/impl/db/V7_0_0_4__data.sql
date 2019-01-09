--
-- Create the data table
--
CREATE TABLE IF NOT EXISTS data (
  id                  bigint(20) NOT NULL AUTO_INCREMENT,
  create_time_ms      bigint(20) NOT NULL,
  effective_time_ms   bigint(20) DEFAULT NULL,
  parent_id           bigint(20) DEFAULT NULL,
  status              tinyint(4) NOT NULL,
  status_time_ms      bigint(20) DEFAULT NULL,
  task_id             bigint(20) DEFAULT NULL,
  feed_id             int(11) NOT NULL,
  type_id             int(11) NOT NULL,
  processor_id        int(11) DEFAULT NULL,
  PRIMARY KEY         (id),
  CONSTRAINT data_feed_id FOREIGN KEY (feed_id) REFERENCES data_feed (id),
  CONSTRAINT data_type_id FOREIGN KEY (type_id) REFERENCES data_type (id),
  CONSTRAINT data_processor_id FOREIGN KEY (processor_id) REFERENCES data_processor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


--
-- Copy data into the data table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN

  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM' > 0) THEN
    INSERT INTO data (id, create_time_ms, effective_time_ms, parent_id, status, status_time_ms, task_id, feed_id, type_id, processor_id)
    SELECT ID, CRT_MS, EFFECT_MS, PARNT_STRM_ID, STAT, STAT_MS, STRM_TASK_ID, FK_FD_ID, FK_STRM_TP_ID, FK_STRM_PROC_ID
    FROM STRM
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM data)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE data AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM data;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
