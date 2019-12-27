-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the meta_type table
--
CREATE TABLE IF NOT EXISTS meta_type (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  name				    varchar(255) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy meta into the meta_type table
--
DROP PROCEDURE IF EXISTS copy_meta_type;
DELIMITER //
CREATE PROCEDURE copy_meta_type ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_TP' > 0) THEN
    INSERT INTO meta_type (id, name)
    SELECT ID, NAME
    FROM STRM_TP
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_type)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE meta_type AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM meta_type;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_meta_type();
DROP PROCEDURE copy_meta_type;

SET SQL_NOTES=@OLD_SQL_NOTES;
