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

CREATE TABLE IF NOT EXISTS property_history (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  update_time        	bigint(20) DEFAULT NULL,
  update_user 			varchar(255) DEFAULT NULL,
  name				    varchar(255) NOT NULL,
  val 				    longtext NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the property table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN
    INSERT INTO property (name, val)
    SELECT NAME, VAL
    FROM GLOB_PROP
    ORDER BY NAME;

    INSERT INTO property_history (update_time, update_user, name, val)
    SELECT UPD_MS, UPD_USER, NAME, VAL
    FROM GLOB_PROP
    ORDER BY NAME;

    DROP TABLE IF EXISTS OLD_GLOB_PROP;
    RENAME TABLE GLOB_PROP TO OLD_GLOB_PROP;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;