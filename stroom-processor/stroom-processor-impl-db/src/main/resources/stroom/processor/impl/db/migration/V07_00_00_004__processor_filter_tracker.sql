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

-- Create the table
CREATE TABLE IF NOT EXISTS `processor_filter_tracker` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `version` int(11) NOT NULL,
  `min_meta_id` bigint(20) NOT NULL,
  `min_event_id` bigint(20) NOT NULL,
  `min_meta_create_ms` bigint(20) DEFAULT NULL,
  `max_meta_create_ms` bigint(20) DEFAULT NULL,
  `meta_create_ms` bigint(20) DEFAULT NULL,
  `last_poll_ms` bigint(20) DEFAULT NULL,
  `last_poll_task_count` int(11) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `meta_count` bigint(20) DEFAULT NULL,
  `event_count` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
