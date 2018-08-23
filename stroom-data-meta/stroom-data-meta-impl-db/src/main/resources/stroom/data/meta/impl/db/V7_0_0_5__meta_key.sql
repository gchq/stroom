--
-- Create the meta_key table
--
CREATE TABLE IF NOT EXISTS meta_key (
  id 				int(11) NOT NULL AUTO_INCREMENT,
  name 		        varchar(100) NOT NULL,
  field_type 		tinyint(4) NOT NULL,
  PRIMARY KEY       (id),
  UNIQUE KEY		name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the meta_key table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_ATR_KEY' > 0) THEN
    INSERT INTO meta_key (id, name, field_type)
    SELECT ID, NAME, FLD_TP
    FROM STRM_ATR_KEY
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_key)
    ORDER BY ID;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_STRM_ATR_KEY' > 0) THEN
    INSERT INTO meta_key (id, name, field_type)
    SELECT ID, NAME, FLD_TP
    FROM OLD_STRM_ATR_KEY
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_key)
    ORDER BY ID;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
