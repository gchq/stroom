--
-- Create the property table
--
CREATE TABLE IF NOT EXISTS property (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  name				    varchar(255) NOT NULL,
  val 				    longtext NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the property table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN
    INSERT INTO property (id, name, val)
    SELECT ID, NAME, VAL
    FROM GLOB_PROP
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM property)
    ORDER BY ID;
    DROP TABLE IF EXISTS OLD_GLOB_PROP;
    RENAME TABLE GLOB_PROP TO OLD_GLOB_PROP;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;