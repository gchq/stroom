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
SET @old_sql_notes=@@sql_notes, sql_notes=0;

CREATE TABLE IF NOT EXISTS doc (
  id 		bigint(20) auto_increment PRIMARY KEY,
  type 		varchar(255) NOT NULL,
  uuid 		varchar(255) NOT NULL,
  name 		varchar(255) NOT NULL,
  data 		longtext,
  UNIQUE 	(type, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL create_docstore_index(
    database(),
    'doc',
    'doc_type_uuid_idx',
    false,
    'type, uuid');

CALL create_docstore_index(
    database(),
    'doc',
    'doc_uuid_idx',
    false,
    'uuid');

SET SQL_NOTES=@OLD_SQL_NOTES;
