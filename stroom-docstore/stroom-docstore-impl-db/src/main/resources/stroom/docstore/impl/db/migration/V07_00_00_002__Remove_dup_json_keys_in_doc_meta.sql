-- ------------------------------------------------------------------------
-- Copyright 2022 Crown Copyright
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
SET @old_sql_notes=@@sql_notes, sql_notes=0;

-- Remove the updateTime key as it was added as a dup of updateTimeMs
-- Idempotent
UPDATE doc d
SET d.data = json_remove(
    CONVERT(d.data USING utf8mb4),
    '$.updateTime')
WHERE d.ext = 'meta'
AND JSON_EXTRACT(CONVERT(d.data USING utf8mb4), '$.updateTime') IS NOT NULL;

-- Remove the createTime key as it was added as a dup of createTimeMs
-- Idempotent
UPDATE doc d
SET d.data = json_remove(
    convert(d.data USING utf8mb4),
    '$.createTime')
WHERE d.ext = 'meta'
AND JSON_EXTRACT(CONVERT(d.data USING utf8mb4), '$.createTime') IS NOT NULL;

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
