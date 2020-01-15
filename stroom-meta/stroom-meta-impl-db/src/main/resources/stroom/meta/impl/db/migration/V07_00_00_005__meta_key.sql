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
-- Create the meta_key table
--
CREATE TABLE IF NOT EXISTS meta_key (
  id 				int(11) NOT NULL AUTO_INCREMENT,
  name 		        varchar(100) NOT NULL,
  field_type 		tinyint(4) NOT NULL,
  PRIMARY KEY       (id),
  UNIQUE KEY		name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the meta_key table
--
DROP PROCEDURE IF EXISTS copy_meta_key;
DELIMITER //
CREATE PROCEDURE copy_meta_key ()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_ATR_KEY' > 0) THEN
    INSERT INTO meta_key (id, name, field_type)
    SELECT ID, NAME, FLD_TP
    FROM STRM_ATR_KEY
    WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM meta_key)
    ORDER BY ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE meta_key AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM meta_key;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_meta_key();
DROP PROCEDURE copy_meta_key;

SET SQL_NOTES=@OLD_SQL_NOTES;
