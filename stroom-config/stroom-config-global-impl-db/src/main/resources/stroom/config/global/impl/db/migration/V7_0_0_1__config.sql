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

DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN

        INSERT INTO config (name, val)
        SELECT NAME, VAL
        FROM GLOB_PROP
        ORDER BY NAME;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE config AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM config_history;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;

        INSERT INTO config_history (update_time, update_user, name, val)
        SELECT UPD_MS, UPD_USER, NAME, VAL
        FROM GLOB_PROP
        ORDER BY NAME;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE config_history AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM config_history;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
