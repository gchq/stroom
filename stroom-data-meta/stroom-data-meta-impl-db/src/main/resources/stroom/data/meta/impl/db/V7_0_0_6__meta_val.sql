--
-- Create the meta_val table
--
CREATE TABLE IF NOT EXISTS meta_val (
  id 				        bigint(20) NOT NULL AUTO_INCREMENT,
  create_time               bigint(20) NOT NULL,
  data_id                   bigint(20) NOT NULL,
  meta_key_id               int(11) NOT NULL,
  val 		                bigint(20) NOT NULL,
  PRIMARY KEY               (id),
  KEY                       meta_val_create_time_idx (create_time),
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
    INSERT INTO meta_val (id, create_time, data_id, meta_key_id, val)
    SELECT ID, CRT_MS, STRM_ID, STRM_ATR_KEY_ID, VAL_NUM
    FROM STRM_ATR_VAL
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_key)
    AND VAL_NUM IS NOT NULL
    ORDER BY ID;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_STRM_ATR_VAL' > 0) THEN
    INSERT INTO meta_val (id, create_time, data_id, meta_key_id, val)
    SELECT ID, CRT_MS, STRM_ID, STRM_ATR_KEY_ID, VAL_NUM
    FROM OLD_STRM_ATR_VAL
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_key)
    AND VAL_NUM IS NOT NULL
    ORDER BY ID;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
