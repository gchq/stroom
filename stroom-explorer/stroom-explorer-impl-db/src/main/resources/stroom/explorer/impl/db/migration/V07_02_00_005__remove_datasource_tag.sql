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
set @old_sql_notes = @@sql_notes, sql_notes = 0;

-- Remove the DataSource tag, as this is not done in code using NodeFlags
-- Trim it to clean up any whitespace if there are other tags in there.
UPDATE explorer_node
SET tags = TRIM(REPLACE(tags, 'DataSource', ''))
WHERE tags LIKE '%DataSource%';

-- If the tags cols is empty null it
UPDATE explorer_node
SET tags = null
WHERE tags = '';

-- Reset to the original value
SET SQL_NOTES = @OLD_SQL_NOTES;
