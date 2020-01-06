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

CALL core_add_column_v1(
    'IDX_VOL',
    'IDX_UUID',
    'varchar(255) default NULL');

-- idempotent
UPDATE IDX_VOL iv
SET iv.IDX_UUID = (
    SELECT i.UUID
    FROM IDX i
    WHERE i.ID = iv.FK_IDX_ID);

CALL core_drop_constraint_v1(
    'IDX_VOL',
    'VOL_FK_IDX_ID',
    'FOREIGN KEY');

-- idempotent
ALTER TABLE IDX_VOL DROP PRIMARY KEY, ADD PRIMARY KEY(FK_VOL_ID, IDX_UUID);

CALL core_drop_column_v1(
    'IDX_VOL',
    'FK_IDX_ID');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
