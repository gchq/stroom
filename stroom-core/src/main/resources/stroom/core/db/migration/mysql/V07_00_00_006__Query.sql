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
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

CALL core_add_column_v1('QUERY', 'DASH_UUID', 'varchar(255) default NULL');

-- idempotent
UPDATE QUERY
INNER JOIN DASH ON (QUERY.DASH_ID = DASH.ID)
SET QUERY.DASH_UUID = DASH.UUID;

CALL core_drop_column_v1('QUERY', 'DASH_ID');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
