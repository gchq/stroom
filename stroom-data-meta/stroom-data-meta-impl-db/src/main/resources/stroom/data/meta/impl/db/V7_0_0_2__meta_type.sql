--
-- Create the data_type table
--
CREATE TABLE IF NOT EXISTS data_type (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  name				    varchar(255) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the data_type table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_TP' > 0) THEN
    INSERT INTO data_type (id, name)
    SELECT ID, NAME
    FROM STRM_TP
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM data_type)
    ORDER BY ID;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_STRM_TP' > 0) THEN
    INSERT INTO data_type (id, name)
    SELECT ID, NAME
    FROM OLD_STRM_TP
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM data_type)
    ORDER BY ID;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;