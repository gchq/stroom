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
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'IDX') THEN

        RENAME TABLE IDX TO OLD_IDX;
    END IF;
END//
DELIMITER ;
CALL rename_idx();
DROP PROCEDURE rename_idx;

-- idempotent
DROP PROCEDURE IF EXISTS rename_idx_shrd;
DELIMITER //
CREATE PROCEDURE rename_idx_shrd ()
BEGIN

    -- idempotent
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'IDX_SHRD') THEN

        RENAME TABLE IDX_SHRD TO OLD_IDX_SHRD;
    END IF;
END//
DELIMITER ;
CALL rename_idx_shrd();
DROP PROCEDURE rename_idx_shrd;

-- idempotent
CALL core_add_column_v1(
    'OLD_IDX_SHRD',
    'IDX_UUID',
    'varchar(255) default NULL');

-- idempotent
UPDATE OLD_IDX_SHRD shard
SET shard.IDX_UUID = (
    SELECT ind.UUID
    FROM OLD_IDX ind
    WHERE ind.ID = shard.FK_IDX_ID);

-- idempotent
-- We need to drop the constraint so we can rename the column
CALL core_drop_constraint_v1(
    'OLD_IDX_SHRD',
    'IDX_SHRD_FK_IDX_ID',
    'FOREIGN KEY');

-- idempotent
-- On some existing databases the constraint is named _SHARD_ not _SHRD_
-- so we attempt to delete both forms of the name.
CALL core_drop_constraint_v1(
    'OLD_IDX_SHRD',
    'IDX_SHARD_FK_IDX_ID',
    'FOREIGN KEY');

-- idempotent
CALL core_rename_column_v1(
    'OLD_IDX_SHRD',
    'FK_IDX_ID',
    'OLD_IDX_ID',
    'int(11) default NULL');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
