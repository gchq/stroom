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

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the table
--
CREATE TABLE IF NOT EXISTS`processor_filter` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `version` int(11) NOT NULL,
  `create_time_ms` bigint(20) NOT NULL,
  `create_user` varchar(255) NOT NULL,
  `update_time_ms` bigint(20) NOT NULL,
  `update_user` varchar(255) NOT NULL,
  `uuid` varchar(255) NOT NULL,
  `fk_processor_id` int(11) NOT NULL,
  `fk_processor_filter_tracker_id` int(11) NOT NULL,
  `data` longtext NOT NULL,
  `priority` int(11) NOT NULL,
  `reprocess` bit(1) DEFAULT b'0',
  `enabled` bit(1) DEFAULT b'0',
  `deleted` bit(1) DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uuid` (`uuid`),
  KEY `processor_filter_fk_processor_id` (`fk_processor_id`),
  KEY `processor_filter_fk_processor_filter_tracker_id` (`fk_processor_filter_tracker_id`),
  CONSTRAINT `processor_filter_fk_processor_filter_tracker_id` FOREIGN KEY (`fk_processor_filter_tracker_id`) REFERENCES `processor_filter_tracker` (`id`),
  CONSTRAINT `processor_filter_fk_processor_id` FOREIGN KEY (`fk_processor_id`) REFERENCES `processor` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
