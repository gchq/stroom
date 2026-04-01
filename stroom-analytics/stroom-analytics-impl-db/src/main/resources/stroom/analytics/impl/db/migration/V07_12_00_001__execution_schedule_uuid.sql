-- ------------------------------------------------------------------------
-- Copyright 2023 Crown Copyright
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

DROP PROCEDURE IF EXISTS V07_12_00_001__execution_schedule_uuid;

DELIMITER $$

CREATE PROCEDURE V07_12_00_001__execution_schedule_uuid ()
BEGIN
    DECLARE object_count integer;

    -- Create UUID in execution_schedule
    SELECT COUNT(1) INTO object_count FROM information_schema.columns
    WHERE table_schema = database() AND table_name = 'execution_schedule' AND column_name = 'uuid';
    IF object_count = 0 THEN
        ALTER TABLE execution_schedule    ADD COLUMN uuid varchar(255) DEFAULT NULL;
        ALTER TABLE execution_schedule    ADD CONSTRAINT UNIQUE INDEX uuid (uuid);
        UPDATE execution_schedule         SET uuid=uuid();
        ALTER TABLE execution_schedule    CHANGE COLUMN uuid uuid varchar(255) NOT NULL;
    END IF;

    -- Populate the new UUID foreign key based on the existing numeric id in execution_history
    SELECT COUNT(1) INTO object_count FROM information_schema.columns
    WHERE table_schema = database() AND table_name = 'execution_history' AND column_name = 'fk_execution_schedule_uuid';
    IF object_count = 0 THEN
        ALTER TABLE execution_history ADD COLUMN fk_execution_schedule_uuid varchar(255) DEFAULT NULL;
        UPDATE execution_history
            JOIN execution_schedule ON execution_history.fk_execution_schedule_id = execution_schedule.id
            SET execution_history.fk_execution_schedule_uuid = execution_schedule.uuid;
    END IF;

    -- Populate the new UUID foreign key in execution_tracker
    SELECT COUNT(1) INTO object_count FROM information_schema.columns
    WHERE table_schema = database() AND table_name = 'execution_tracker' AND column_name = 'fk_execution_schedule_uuid';
    IF object_count = 0 THEN
        ALTER TABLE execution_tracker ADD COLUMN fk_execution_schedule_uuid varchar(255) DEFAULT NULL;
        UPDATE execution_tracker
            JOIN execution_schedule ON execution_tracker.fk_execution_schedule_id = execution_schedule.id
            SET execution_tracker.fk_execution_schedule_uuid = execution_schedule.uuid;
    END IF;

    -- Remove foreign key constraints so we can eventually drop id
    SELECT COUNT(1) INTO object_count FROM information_schema.table_constraints
    WHERE table_schema = database() AND table_name = 'execution_history' AND constraint_name = 'execution_history_execution_schedule_id' AND constraint_type = 'FOREIGN KEY';
    IF object_count > 0 THEN
        ALTER TABLE execution_history DROP FOREIGN KEY execution_history_execution_schedule_id;
    END IF;

    SELECT COUNT(1) INTO object_count FROM information_schema.table_constraints
    WHERE table_schema = database() AND table_name = 'execution_tracker' AND constraint_name = 'execution_tracker_execution_schedule_id' AND constraint_type = 'FOREIGN KEY';
    IF object_count > 0 THEN
        ALTER TABLE execution_tracker DROP FOREIGN KEY execution_tracker_execution_schedule_id;
    END IF;

    -- Add new foreign key constraints on UUID columns
    SELECT COUNT(1) INTO object_count FROM information_schema.table_constraints
    WHERE table_schema = database() AND table_name = 'execution_history' AND constraint_name = 'execution_history_fk_execution_schedule_uuid' AND constraint_type = 'FOREIGN KEY';
    IF object_count = 0 THEN
        ALTER TABLE execution_history
            ADD CONSTRAINT execution_history_fk_execution_schedule_uuid
                FOREIGN KEY (fk_execution_schedule_uuid) REFERENCES execution_schedule (uuid);
    END IF;

    SELECT COUNT(1) INTO object_count FROM information_schema.table_constraints
    WHERE table_schema = database() AND table_name = 'execution_tracker' AND constraint_name = 'execution_tracker_fk_execution_schedule_uuid' AND constraint_type = 'FOREIGN KEY';
    IF object_count = 0 THEN
        ALTER TABLE execution_tracker
            ADD CONSTRAINT execution_tracker_fk_execution_schedule_uuid
                FOREIGN KEY (fk_execution_schedule_uuid) REFERENCES execution_schedule (uuid);
    END IF;

    -- Replace primary key of tracker
    -- Check if fk_execution_schedule_uuid is already the Primary Key
    SELECT COUNT(1) INTO object_count FROM information_schema.key_column_usage
    WHERE table_schema = database() AND table_name = 'execution_tracker' AND constraint_name = 'PRIMARY' AND column_name = 'fk_execution_schedule_uuid';
    IF object_count = 0 THEN
        ALTER TABLE execution_tracker MODIFY COLUMN fk_execution_schedule_uuid varchar(255) NOT NULL;
        -- Only in this block if the PK is not already on fk_execution_schedule_uuid so can just drop PK
        ALTER TABLE execution_tracker DROP PRIMARY KEY;
        ALTER TABLE execution_tracker ADD PRIMARY KEY (fk_execution_schedule_uuid);
    END IF;

    -- Drop old id-based foreign keys and id in execution_schedule.
    -- Currently commented out for safety in testing.
    -- SELECT COUNT(1) INTO object_count FROM information_schema.columns WHERE table_schema = database() AND table_name = 'execution_history' AND column_name = 'fk_execution_schedule_id';
    -- IF object_count > 0 THEN
    --    ALTER TABLE execution_history DROP COLUMN fk_execution_schedule_id;
    -- END IF;
    --
    -- SELECT COUNT(1) INTO object_count FROM information_schema.columns WHERE table_schema = database() AND table_name = 'execution_tracker' AND column_name = 'fk_execution_schedule_id';
    -- IF object_count > 0 THEN
    --    ALTER TABLE execution_tracker DROP COLUMN fk_execution_schedule_id;
    -- END IF;
    --
    -- SELECT COUNT(1) INTO object_count FROM information_schema.columns WHERE table_schema = database() AND table_name = 'execution_schedule' AND column_name = 'id';
    -- IF object_count > 0 THEN
    --    ALTER TABLE execution_schedule DROP COLUMN id;
    -- END IF;

END $$

DELIMITER ;

CALL V07_12_00_001__execution_schedule_uuid;

DROP PROCEDURE IF EXISTS V07_12_00_001__execution_schedule_uuid;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
