--
-- Create the file_feed_path table
--
CREATE TABLE IF NOT EXISTS file_feed_path (
  id 				int(11) NOT NULL AUTO_INCREMENT,
  name 		        varchar(255) NOT NULL,
  path 		        varchar(255) NOT NULL,
  PRIMARY KEY       (id),
  UNIQUE KEY		name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the file_feed_path table
--
DROP PROCEDURE IF EXISTS copy_file_feed_path;
DELIMITER //
CREATE PROCEDURE copy_file_feed_path ()
BEGIN
  -- TODO get rid of OLD_ table logic, see V7_0_0_1__config.sql as an example
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'FD' > 0) THEN
    INSERT INTO file_feed_path (id, name, path) SELECT ID, NAME, ID FROM FD WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_feed_path) ORDER BY ID;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_FD' > 0) THEN
    INSERT INTO file_feed_path (id, name, path) SELECT ID, NAME, ID FROM OLD_FD WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_feed_path) ORDER BY ID;
  END IF;
END//
DELIMITER ;
CALL copy_file_feed_path();
DROP PROCEDURE copy_file_feed_path;
