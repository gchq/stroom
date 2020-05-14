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
-- Create the processor_node table
--
CREATE TABLE IF NOT EXISTS processor_node (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  name				    varchar(255) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy node name into the processor_node table
--
DROP PROCEDURE IF EXISTS copy_processor_node;
DELIMITER //
CREATE PROCEDURE copy_processor_node ()
BEGIN
    -- Check again so it is idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'ND') THEN

        RENAME TABLE ND TO OLD_ND;
    END IF;

    -- Check again so it is idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'OLD_ND') THEN

        INSERT INTO processor_node (id, name)
        SELECT ID, NAME
        FROM OLD_ND
        WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM processor_node)
        ORDER BY ID;

        -- Work out what to set our auto_increment start value to
        SELECT CONCAT('ALTER TABLE processor_node AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM processor_node;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;

END//
DELIMITER ;
CALL copy_processor_node();
DROP PROCEDURE copy_processor_node;

SET SQL_NOTES=@OLD_SQL_NOTES;
