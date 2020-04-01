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
ALTER TABLE doc MODIFY COLUMN data LONGBLOB;

CALL core_drop_constraint_v1(
    'doc',
    'type',
    'INDEX');

CALL core_add_column_v1(
    'doc',
    'ext',
    'varchar(255) DEFAULT NULL');

-- idempotent
UPDATE doc
SET ext = "meta";

CALL core_create_index_v1(
    'doc',
    'doc_type_uuid_ext_idx',
    true,
    'type, uuid, ext');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
