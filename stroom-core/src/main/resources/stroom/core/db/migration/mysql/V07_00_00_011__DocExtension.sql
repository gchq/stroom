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

ALTER TABLE doc MODIFY COLUMN data LONGBLOB;
ALTER TABLE doc DROP INDEX type;
ALTER TABLE doc ADD COLUMN ext varchar(255) DEFAULT NULL;
ALTER TABLE doc ADD CONSTRAINT type_uuid_ext UNIQUE (type, uuid, ext);
UPDATE doc SET ext = "meta";

CREATE INDEX doc_type_uuid_ext_idx ON doc (type, uuid, ext);