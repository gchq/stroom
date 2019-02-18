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
-- Copy data into the file_type_path table
--
DROP PROCEDURE IF EXISTS copy_file_type_path;
DELIMITER //
CREATE PROCEDURE copy_file_type_path ()
BEGIN
  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'STRM_TP') THEN

    SET @insert_sql=''
        ' INSERT INTO file_type_path (id, name, path)'
        ' SELECT ID, NAME, PATH'
        ' FROM STRM_TP'
        ' WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_type_path)'
        ' ORDER BY ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE file_type_path AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM file_type_path;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_file_type_path();
DROP PROCEDURE copy_file_type_path;
