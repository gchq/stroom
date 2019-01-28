--
-- Create the config table
--
CREATE TABLE IF NOT EXISTS config (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  name				    varchar(255) NOT NULL,
  val 				    longtext NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the config table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN
    INSERT INTO config (id, version, create_time_ms, create_user, update_time_ms, update_user, name, val)
    SELECT ID, 1, CRT_MS, CRT_USER, UPD_MS, UPD_USER, NAME, VAL
    FROM GLOB_PROP
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM activity)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE config AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM config;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
