-- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
-- Copyright 2024-2026 Crown Copyright
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
-- ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-- Add version column for optimistic concurrency control.
-- The version UUID is currently embedded inside the meta JSON blob ($.version).
-- Extract it to a dedicated column so the DB can enforce version matching atomically.

-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

-- Step 1: Add the column with a default that lets us identify unpopulated rows.
ALTER TABLE doc ADD COLUMN version varchar(36) NOT NULL DEFAULT '';

-- Step 2: Populate from existing meta JSON.
-- Populate from existing meta JSON stored in doc_data.json_data.
UPDATE doc d
JOIN doc_data dd ON dd.fk_doc_id = d.id AND dd.ext = 'meta'
SET d.version = JSON_UNQUOTE(JSON_EXTRACT(dd.json_data, '$.version'))
WHERE dd.json_data IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(dd.json_data, '$.version')) IS NOT NULL;

-- Step 3: Fallback — generate a UUID for rows with missing/invalid JSON
-- (e.g. those with meta stored in text_data rather than json_data).
UPDATE doc
SET version = UUID()
WHERE version = '';

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
