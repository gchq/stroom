-- ------------------------------------------------------------------------
-- Copyright 2024 Crown Copyright
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

DROP TABLE IF EXISTS `permission_app`;
DROP TABLE IF EXISTS `permission_app_id`;

--
-- Create the application permission id table
--
CREATE TABLE IF NOT EXISTS `permission_app_id` (
  `id` tinyint UNSIGNED NOT NULL AUTO_INCREMENT,
  `permission` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_app_id_permission_idx` (`permission`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Add app permission names into the app permission id table.
--
INSERT INTO `permission_app_id` (`permission`)
SELECT DISTINCT(permission)
FROM app_permission;

--
-- Create the new application permission table.
--
CREATE TABLE IF NOT EXISTS `permission_app` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_uuid` varchar(255) NOT NULL,
  `permission_id` tinyint UNSIGNED NOT NULL,
  PRIMARY KEY (`id`),
  KEY `permission_app_user_uuid` (`user_uuid`),
  UNIQUE KEY `permission_app_user_uuid_permission_id_idx` (`user_uuid`,`permission_id`),
  CONSTRAINT `permission_app_user_uuid` FOREIGN KEY (`user_uuid`) REFERENCES `stroom_user` (`uuid`),
  CONSTRAINT `permission_app_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permission_app_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Copy permission values to the new table.
--
INSERT INTO permission_app (user_uuid, permission_id)
SELECT ap.user_uuid, pai.id
FROM app_permission ap
JOIN permission_app_id pai
ON (pai.permission = ap.permission);

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
