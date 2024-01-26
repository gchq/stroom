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

DROP PROCEDURE IF EXISTS modify_processor;
DELIMITER //
CREATE PROCEDURE modify_processor ()
BEGIN
     DECLARE object_count integer;

     SELECT COUNT(1)
     INTO object_count
     FROM information_schema.table_constraints
     WHERE table_schema = database()
     AND table_name = 'processor'
     AND constraint_name = 'processor_pipeline_uuid';

     IF object_count = 1 THEN
         ALTER TABLE processor DROP INDEX processor_pipeline_uuid;
     END IF;

     SELECT COUNT(1)
     INTO object_count
     FROM information_schema.table_constraints
     WHERE table_schema = database()
     AND table_name = 'processor'
     AND constraint_name = 'processor_task_type_pipeline_uuid';

     IF object_count = 0 THEN
         CREATE UNIQUE INDEX processor_task_type_pipeline_uuid ON processor (task_type, pipeline_uuid);
     END IF;
END//
DELIMITER ;
CALL modify_processor();
DROP PROCEDURE modify_processor;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
