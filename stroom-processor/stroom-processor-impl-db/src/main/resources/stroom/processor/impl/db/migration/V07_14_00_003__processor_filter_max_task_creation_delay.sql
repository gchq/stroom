-- ------------------------------------------------------------------------
-- Copyright 2025 Crown Copyright
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

DROP PROCEDURE IF EXISTS V07_14_00_003__processor_filter_max_task_creation_delay;

DELIMITER $$

CREATE PROCEDURE V07_14_00_003__processor_filter_max_task_creation_delay ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'processor_filter'
    AND column_name = 'max_task_creation_delay';

    IF object_count = 0 THEN
        -- How long this filter will wait, at most, before task creation polls it again after
        -- polls that created no tasks, e.g. '30s'. Overrides the cluster wide
        -- skipNonProducingFiltersMaxDuration property so that latency sensitive filters, such
        -- as those raising alerts from infrequent data, can be polled more eagerly than the
        -- rest. Null means use the cluster wide property.
        ALTER TABLE processor_filter ADD COLUMN max_task_creation_delay varchar(255) DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_14_00_003__processor_filter_max_task_creation_delay;

DROP PROCEDURE IF EXISTS V07_14_00_003__processor_filter_max_task_creation_delay;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
