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
-- Create the meta_processor table
--
-- processor_id comes from the processor table in stroom-process but there is no FK
-- between them
CREATE TABLE IF NOT EXISTS meta_processor (
  id 				    int(11) NOT NULL AUTO_INCREMENT,
  processor_uuid 	    varchar(255) DEFAULT NULL,
  pipeline_uuid 	    varchar(255) DEFAULT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            processor_uuid (processor_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the meta_processor table
--
DROP PROCEDURE IF EXISTS copy_meta_processor;
DELIMITER //
CREATE PROCEDURE copy_meta_processor ()
BEGIN
    -- If table exists (it may not if this migration runs before core stroom's) then migrate its data,
    -- if it doesn't exist then it won't ever have data to migrate
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'STRM_PROC' > 0) THEN
    -- Copy data into the meta_processor table, use ID predicate to make it re-runnable
    INSERT
    INTO meta_processor (id, processor_uuid, pipeline_uuid)
    SELECT SP.ID, P.UUID, P.UUID
    FROM STRM_PROC SP
    JOIN PIPE P ON (P.ID = SP.FK_PIPE_ID)
    WHERE SP.ID > (SELECT COALESCE(MAX(id), 0) FROM meta_processor)
    ORDER BY SP.ID;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE meta_processor AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM meta_processor;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
END//
DELIMITER ;
CALL copy_meta_processor();
DROP PROCEDURE copy_meta_processor;

SET SQL_NOTES=@OLD_SQL_NOTES;
