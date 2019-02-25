--
-- Create the file_meta_volume table
--
CREATE TABLE IF NOT EXISTS file_meta_volume (
  meta_id 				bigint(20) NOT NULL,
  file_volume_id		int(11) NOT NULL,
  PRIMARY KEY       (meta_id, file_volume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the file_meta_volume table
--
DROP PROCEDURE IF EXISTS copy_file_meta_volume;
DELIMITER //
CREATE PROCEDURE copy_file_meta_volume ()
BEGIN
  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'STRM_VOL') THEN

    SET @insert_sql=''
        ' INSERT INTO file_meta_volume (meta_id, file_volume_id)'
        ' SELECT FK_STRM_ID, FK_VOL_ID'
        ' FROM STRM_VOL'
        ' WHERE FK_STRM_ID > (SELECT COALESCE(MAX(meta_id), 0) FROM file_meta_volume)'
        ' ORDER BY FK_STRM_ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_file_meta_volume();
DROP PROCEDURE copy_file_meta_volume;
