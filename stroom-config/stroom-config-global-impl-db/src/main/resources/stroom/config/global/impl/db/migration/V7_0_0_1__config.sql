--
-- Create the property table
--
CREATE TABLE IF NOT EXISTS config (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  name				    varchar(255) NOT NULL,
  val 				    longtext NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS config_history (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  update_time        	bigint(20) DEFAULT NULL,
  update_user 			varchar(255) DEFAULT NULL,
  name				    varchar(255) NOT NULL,
  val 				    longtext NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the config table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN
        INSERT INTO config (name, val)
        SELECT NAME, VAL
        FROM GLOB_PROP
        ORDER BY NAME;

        INSERT INTO config_history (update_time, update_user, name, val)
        SELECT UPD_MS, UPD_USER, NAME, VAL
        FROM GLOB_PROP
        ORDER BY NAME;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'OLD_GLOB_PROP' > 0) THEN
      INSERT INTO config (name, val)
      SELECT NAME, VAL
      FROM OLD_GLOB_PROP
      ORDER BY NAME;

      INSERT INTO config_history (update_time, update_user, name, val)
      SELECT UPD_MS, UPD_USER, NAME, VAL
      FROM OLD_GLOB_PROP
      ORDER BY NAME;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;