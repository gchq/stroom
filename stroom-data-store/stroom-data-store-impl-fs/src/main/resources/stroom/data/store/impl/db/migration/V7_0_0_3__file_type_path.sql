--
-- Create the file_type_path table
--
CREATE TABLE IF NOT EXISTS file_type_path (
  id 				int(11) NOT NULL AUTO_INCREMENT,
  name 		        varchar(255) NOT NULL,
  path 		        varchar(255) NOT NULL,
  PRIMARY KEY       (id),
  UNIQUE KEY		name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the file_feed_path table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_TP' > 0) THEN
  INSERT INTO file_type_path (id, name, path) SELECT ID, NAME, PATH FROM STRM_TP WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_type_path) ORDER BY ID;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_STRM_TP' > 0) THEN
    INSERT INTO file_type_path (id, name, path) SELECT ID, NAME, PATH FROM OLD_STRM_TP WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_type_path) ORDER BY ID;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
