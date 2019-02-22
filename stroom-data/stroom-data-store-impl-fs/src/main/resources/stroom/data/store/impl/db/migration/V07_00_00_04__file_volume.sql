--
-- Create the file_volume_state table
--
CREATE TABLE IF NOT EXISTS file_volume_state (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  bytes_used                bigint(20) DEFAULT NULL,
  bytes_free                bigint(20) DEFAULT NULL,
  bytes_total               bigint(20) DEFAULT NULL,
  update_time_ms            bigint(20) DEFAULT NULL,
  PRIMARY KEY       (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Create the file_volume table
--
CREATE TABLE IF NOT EXISTS file_volume (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  create_time_ms            bigint(20) NOT NULL,
  create_user               varchar(255) NOT NULL,
  update_time_ms            bigint(20) NOT NULL,
  update_user               varchar(255) NOT NULL,
  path 		                varchar(255) NOT NULL,
  status	                tinyint(4) NOT NULL,
  byte_limit                bigint(20) DEFAULT NULL,
  fk_file_volume_state_id   int(11) NOT NULL,
  PRIMARY KEY       (id),
  UNIQUE KEY		path (path),
  KEY file_volume_fk_file_volume_state_id (fk_file_volume_state_id),
  CONSTRAINT file_volume_fk_file_volume_state_id FOREIGN KEY (fk_file_volume_state_id) REFERENCES file_volume_state (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the file_volume_state table
--
DROP PROCEDURE IF EXISTS copy_file_volume_state;
DELIMITER //
CREATE PROCEDURE copy_file_volume_state ()
BEGIN
  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'VOL_STATE') THEN

    SET @insert_sql=''
        ' INSERT INTO file_volume_state (id, version, bytes_used, bytes_free, bytes_total, update_time_ms)'
        ' SELECT ID, VER, BYTES_USED, BYTES_FREE, BYTES_TOTL, STAT_MS'
        ' FROM VOL_STATE'
        ' WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_volume_state)'
        ' ORDER BY ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE file_volume_state AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM file_volume_state;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_file_volume_state();
DROP PROCEDURE copy_file_volume_state;

--
-- Copy data into the file_volume table
--
DROP PROCEDURE IF EXISTS copy_file_volume;
DELIMITER //
CREATE PROCEDURE copy_file_volume ()
BEGIN
  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'VOL') THEN

    SET @insert_sql=''
        ' INSERT INTO file_volume (id, version, create_time_ms, create_user, update_time_ms, update_user, path, status, byte_limit, fk_file_volume_state_id)'
        ' SELECT ID, VER, CRT_MS, CRT_USER, UPD_MS, UPD_USER, PATH, STRM_STAT, BYTES_LMT, FK_VOL_STATE_ID'
        ' FROM VOL'
        ' WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM file_volume)'
        ' ORDER BY ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE file_volume AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM file_volume;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_file_volume();
DROP PROCEDURE copy_file_volume;