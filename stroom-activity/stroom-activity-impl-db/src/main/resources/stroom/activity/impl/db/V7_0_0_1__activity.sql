--
-- Create the activity table
--
CREATE TABLE IF NOT EXISTS activity (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  user_id               varchar(255) NOT NULL,
  json                  longtext,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the activity table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'ACTIVITY' > 0) THEN
    INSERT INTO activity (id, version, create_time_ms, create_user, update_time_ms, update_user, user_id, json)
    SELECT ID, 1, CRT_MS, CRT_USER, UPD_MS, UPD_USER, USER_ID, JSON
    FROM ACTIVITY
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM activity)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE activity AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM activity;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;