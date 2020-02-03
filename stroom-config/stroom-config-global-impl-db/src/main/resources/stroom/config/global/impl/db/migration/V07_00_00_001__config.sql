-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

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
DROP PROCEDURE IF EXISTS copy_config;
DELIMITER //
CREATE PROCEDURE copy_config ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'GLOB_PROP' > 0) THEN
    INSERT INTO config (
        id,
        version,
        create_time_ms,
        create_user,
        update_time_ms,
        update_user,
        name,
        val)
    SELECT
        ID,
        1,
        IFNULL(CRT_MS, 0),
        IFNULL(CRT_USER, 'UNKNOWN'),
        IFNULL(UPD_MS, 0),
        IFNULL(UPD_USER, 'UNKNOWN'),
        NAME,
        VAL
    FROM GLOB_PROP
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM config)
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
CALL copy_config();
DROP PROCEDURE copy_config;

SET SQL_NOTES=@OLD_SQL_NOTES;
