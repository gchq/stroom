--
-- Create the meta_val table
--
CREATE TABLE IF NOT EXISTS meta_val (
  id 				        bigint(20) NOT NULL AUTO_INCREMENT,
  create_time_ms            bigint(20) NOT NULL,
  data_id                   bigint(20) NOT NULL,
  meta_key_id               int(11) NOT NULL,
  val 		                bigint(20) NOT NULL,
  PRIMARY KEY               (id),
  KEY                       meta_val_create_time_idx (create_time_ms),
  KEY                       meta_val_data_id_idx (data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the meta_val table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_ATR_VAL' > 0) THEN
    INSERT INTO meta_val (id, create_time_ms, data_id, meta_key_id, val)
    SELECT ID, CRT_MS, STRM_ID, STRM_ATR_KEY_ID, VAL_NUM
    FROM STRM_ATR_VAL
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_key)
    AND VAL_NUM IS NOT NULL
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE meta_val AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM meta_val;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
