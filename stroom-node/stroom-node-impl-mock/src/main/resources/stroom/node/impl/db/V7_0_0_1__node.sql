--
-- Create the node table
--
CREATE TABLE IF NOT EXISTS node (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  url                   varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  priority              smallint(6) NOT NULL,
  enabled               bit(1) NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the node table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'ND' > 0) THEN
    INSERT INTO node (id, version, create_time_ms, create_user, update_time_ms, update_user, url, name, priority, enabled)
    SELECT ID, 1, CRT_MS, CRT_USER, UPD_MS, UPD_USER, CLSTR_URL, NAME, PRIOR, ENBL
    FROM ND
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM node)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE node AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM node;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;