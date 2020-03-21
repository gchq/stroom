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

-- stop note level warnings about objects (not)? existing
set @old_sql_notes=@@sql_notes, sql_notes=0;

-- idempotent
DROP PROCEDURE IF EXISTS rename_idx;
DELIMITER //
CREATE PROCEDURE rename_idx ()
BEGIN
    -- idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'IDX') THEN

        RENAME TABLE IDX TO OLD_IDX;
    END IF;
END//
DELIMITER ;
CALL rename_idx();
DROP PROCEDURE rename_idx;

-- idempotent
DROP PROCEDURE IF EXISTS rename_idx_vol;
DELIMITER //
CREATE PROCEDURE rename_idx_vol ()
BEGIN

    -- idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'IDX_VOL') THEN

        RENAME TABLE IDX_VOL TO OLD_IDX_VOL;
    END IF;
END//
DELIMITER ;
CALL rename_idx_vol();
DROP PROCEDURE rename_idx_vol;

CALL core_add_column_v1(
    'OLD_IDX_VOL',
    'IDX_UUID',
    'varchar(255) default NULL');

-- idempotent
UPDATE OLD_IDX_VOL iv
SET iv.IDX_UUID = (
    SELECT i.UUID
    FROM OLD_IDX i
    WHERE i.ID = iv.FK_IDX_ID);

-- idempotent
CALL core_drop_constraint_v1(
    'OLD_IDX_VOL',
    'VOL_FK_IDX_ID',
    'FOREIGN KEY');

-- idempotent (drops then adds)
ALTER TABLE OLD_IDX_VOL
    DROP PRIMARY KEY,
    ADD PRIMARY KEY(FK_VOL_ID, IDX_UUID);

-- idempotent
CALL core_drop_column_v1(
    'OLD_IDX_VOL',
    'FK_IDX_ID');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
