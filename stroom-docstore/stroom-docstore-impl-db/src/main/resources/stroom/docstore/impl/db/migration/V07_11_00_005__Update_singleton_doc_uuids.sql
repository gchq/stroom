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

-- Previously these three singleton docs had randomly generated UUIDs on each
-- stroom cluster. This complicates imp/exp, so they have all been changed to
-- have fixed and hard coded UUIDs that are the same in all envs.

-- UUID defined in stroom.receive.rules.shared.ReceiveDataRules
SET @new_uuid = '0f62d6c7-fb19-4596-8f2f-f14adbb5c7be';

-- Use regexp_replace rather than json_replace as the latter results in the property order changing
UPDATE doc
SET
  uuid = @new_uuid,
  data = regexp_replace(
               convert(data using UTF8MB4),
               concat('("uuid"\\s*:\\s*")', json_unquote(json_extract(convert(data using UTF8MB4), '$.uuid')), '(")'),
               concat('$1', @new_uuid, '$2'))
WHERE type = 'ReceiveDataRuleSet';

-- UUID defined in stroom.data.retention.shared.DataRetentionRules
SET @new_uuid = 'b81571f2-5a15-4cf6-94ce-0456164bf44a';

-- Use regexp_replace rather than json_replace as the latter results in the property order changing
UPDATE doc
SET
  uuid = @new_uuid,
  data = regexp_replace(
               convert(data using UTF8MB4),
               concat('("uuid"\\s*:\\s*")', json_unquote(json_extract(convert(data using UTF8MB4), '$.uuid')), '(")'),
               concat('$1', @new_uuid, '$2'))
WHERE type = 'DataRetentionRules';

-- UUID defined in stroom.receive.content.shared.ContentTemplates
SET @new_uuid = '4f05f416-c5e6-48ff-aee7-bd905a1cd7a7';

-- Use regexp_replace rather than json_replace as the latter results in the property order changing
UPDATE doc
SET
  uuid = @new_uuid,
  data = regexp_replace(
               convert(data using UTF8MB4),
               concat('("uuid"\\s*:\\s*")', json_unquote(json_extract(convert(data using UTF8MB4), '$.uuid')), '(")'),
               concat('$1', @new_uuid, '$2'))
WHERE type = 'ContentTemplates';

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
