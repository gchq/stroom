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

CREATE TABLE IF NOT EXISTS `processor_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` int NOT NULL,
  `fk_processor_filter_id` int NOT NULL,
  `fk_processor_node_id` int DEFAULT NULL,
  `fk_processor_feed_id` int DEFAULT NULL,
  `create_time_ms` bigint DEFAULT NULL,
  `start_time_ms` bigint DEFAULT NULL,
  `end_time_ms` bigint DEFAULT NULL,
  `status` tinyint NOT NULL,
  `status_time_ms` bigint DEFAULT NULL,
  `meta_id` bigint NOT NULL,
  `data` longtext,
  PRIMARY KEY (`id`),
  KEY `processor_task_fk_processor_filter_id` (`fk_processor_filter_id`),
  KEY `processor_task_fk_processor_node_id` (`fk_processor_node_id`),
  KEY `processor_task_fk_processor_feed_id` (`fk_processor_feed_id`),
  KEY `processor_task_status_idx` (`status`),
  KEY `processor_task_meta_id_idx` (`meta_id`),
  CONSTRAINT `processor_task_fk_processor_feed_id` FOREIGN KEY (`fk_processor_feed_id`) REFERENCES `processor_feed` (`id`),
  CONSTRAINT `processor_task_fk_processor_filter_id` FOREIGN KEY (`fk_processor_filter_id`) REFERENCES `processor_filter` (`id`),
  CONSTRAINT `processor_task_fk_processor_node_id` FOREIGN KEY (`fk_processor_node_id`) REFERENCES `processor_node` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
