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

-- --------------------------------------------------

--
-- Create the table
--
CREATE TABLE IF NOT EXISTS `analytic_processor_filter` (
  `uuid` varchar(255) NOT NULL,
  `version` int NOT NULL,
  `create_time_ms` bigint NOT NULL,
  `create_user` varchar(255) NOT NULL,
  `update_time_ms` bigint NOT NULL,
  `update_user` varchar(255) NOT NULL,
  `analytic_uuid` varchar(255) NOT NULL,
  `expression` longtext DEFAULT NULL,
  `min_meta_create_time_ms` bigint DEFAULT NULL,
  `max_meta_create_time_ms` bigint DEFAULT NULL,
  `node` varchar(255) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`uuid`),
  KEY `analytic_processor_filter_analytic_uuid_idx` (`analytic_uuid`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `analytic_processor_filter_tracker` (
  `fk_analytic_processor_filter_uuid` varchar(255) NOT NULL,
  `last_poll_ms` bigint DEFAULT NULL,
  `last_poll_task_count` int DEFAULT NULL,
  `last_meta_id` bigint DEFAULT NULL,
  `last_event_id` bigint DEFAULT NULL,
  `last_event_time` bigint DEFAULT NULL,
  `meta_count` bigint DEFAULT NULL,
  `event_count` bigint DEFAULT NULL,
  `message` longtext DEFAULT NULL,
  PRIMARY KEY (`fk_analytic_processor_filter_uuid`),
  CONSTRAINT `fk_analytic_processor_filter_uuid`
    FOREIGN KEY (`fk_analytic_processor_filter_uuid`)
    REFERENCES `analytic_processor_filter` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `analytic_notification` (
  `uuid` varchar(255) NOT NULL,
  `version` int NOT NULL,
  `create_time_ms` bigint NOT NULL,
  `create_user` varchar(255) NOT NULL,
  `update_time_ms` bigint NOT NULL,
  `update_user` varchar(255) NOT NULL,
  `analytic_uuid` varchar(255) NOT NULL,
  `config` longtext DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`uuid`),
  KEY `analytic_notification_analytic_uuid_idx` (`analytic_uuid`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `analytic_notification_state` (
  `fk_analytic_notification_uuid` varchar(255) NOT NULL,
  `last_execution_time` bigint DEFAULT NULL,
  `message` longtext DEFAULT NULL,
  PRIMARY KEY (`fk_analytic_notification_uuid`),
  CONSTRAINT `fk_analytic_notification_uuid`
    FOREIGN KEY (`fk_analytic_notification_uuid`)
    REFERENCES `analytic_notification` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------------------------------------

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=2 tabstop=2 expandtab:
