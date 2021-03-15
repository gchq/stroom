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
-- Create the meta table
--
CREATE TABLE IF NOT EXISTS `meta` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` bigint NOT NULL,
  `effective_time` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL,
  `status_time` bigint DEFAULT NULL,
  `feed_id` int NOT NULL,
  `type_id` int NOT NULL,
  `processor_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `meta_create_time` (`create_time`),
  KEY `meta_feed_id_create_time` (`feed_id`,`create_time`),
  KEY `meta_feed_id_effective_time` (`feed_id`,`effective_time`),
  KEY `meta_processor_id_create_time` (`processor_id`,`create_time`),
  KEY `meta_parent_id` (`parent_id`),
  KEY `meta_status` (`status`),
  KEY `meta_type_id` (`type_id`),
  CONSTRAINT `meta_feed_id` FOREIGN KEY (`feed_id`) REFERENCES `meta_feed` (`id`),
  CONSTRAINT `meta_processor_id` FOREIGN KEY (`processor_id`) REFERENCES `meta_processor` (`id`),
  CONSTRAINT `meta_type_id` FOREIGN KEY (`type_id`) REFERENCES `meta_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
