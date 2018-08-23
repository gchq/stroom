--
-- Create the data_volume table
--
CREATE TABLE IF NOT EXISTS data_volume (
  data_id 				bigint(20) NOT NULL,
  volume_id				int(11) NOT NULL,
  PRIMARY KEY       (data_id, volume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the data_volume table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_VOL' > 0) THEN
    INSERT INTO data_volume (data_id, volume_id) SELECT FK_STRM_ID, FK_VOL_ID FROM STRM_VOL WHERE FK_STRM_ID > (SELECT COALESCE(MAX(data_id), 0) FROM data_volume) ORDER BY FK_STRM_ID;
    RENAME TABLE STRM_VOL TO OLD_STRM_VOL;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;
