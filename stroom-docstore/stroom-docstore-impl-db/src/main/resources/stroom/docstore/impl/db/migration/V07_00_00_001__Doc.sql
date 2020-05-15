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

CREATE TABLE IF NOT EXISTS `doc` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(255) NOT NULL,
  `uuid` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `data` longblob,
  `ext` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `doc_type_uuid_idx` (`type`,`uuid`),
  KEY `doc_uuid_idx` (`uuid`),
  KEY `doc_type_uuid_ext_idx` (`type`,`uuid`,`ext`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;
