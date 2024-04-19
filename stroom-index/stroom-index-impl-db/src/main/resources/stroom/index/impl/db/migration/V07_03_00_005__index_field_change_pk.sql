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

DELIMITER $$

DROP PROCEDURE IF EXISTS modify_field_source$$

-- The surrogate PK results in fields from different indexes all being mixed together
-- in the PK index, which causes deadlocks in batch upserts due to gap locks.
-- Change the PK to be (fk_index_field_source_id, name) which should keep the fields
-- together.

CREATE PROCEDURE modify_field_source ()
BEGIN

    -- Remove existing PK
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.columns
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'index_field'
            AND COLUMN_NAME = 'id') THEN

        ALTER TABLE index_field DROP COLUMN id;
    END IF;

    -- Add the new PK
    IF NOT EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.table_constraints
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'index_field'
            AND CONSTRAINT_NAME = 'PRIMARY') THEN

        ALTER TABLE index_field ADD PRIMARY KEY (fk_index_field_source_id, name);
    END IF;

    -- Remove existing index that is now served by PK
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.table_constraints
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'index_field'
            AND CONSTRAINT_NAME = 'index_field_source_id_name') THEN

        ALTER TABLE index_field DROP INDEX index_field_source_id_name;
    END IF;

END $$

DELIMITER ;

CALL modify_field_source();

DROP PROCEDURE IF EXISTS modify_field_source;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
