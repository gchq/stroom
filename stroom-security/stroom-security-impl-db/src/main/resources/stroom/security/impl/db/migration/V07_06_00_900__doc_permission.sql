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

DROP TABLE IF EXISTS `permission_doc`;
DROP TABLE IF EXISTS `permission_doc_id`;
DROP TABLE IF EXISTS `permission_doc_create`;
DROP TABLE IF EXISTS `permission_doc_type_id`;

--
-- Create the permission id table
--
CREATE TABLE IF NOT EXISTS `permission_doc_id` (
  `id` tinyint UNSIGNED NOT NULL,
  `permission` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_doc_id_permission_idx` (`permission`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Add permission names into the id table.
--
INSERT INTO `permission_doc_id` (`id`, `permission`)
VALUES
(10, "Use"),
(20, "Read"),
(30, "Update"),
(40, "Delete"),
(50, "Owner");

--
-- Create the new permission table.
--
CREATE TABLE IF NOT EXISTS `permission_doc` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_uuid` varchar(255) NOT NULL,
  `doc_uuid` varchar(255) NOT NULL,
  `permission_id` tinyint UNSIGNED NOT NULL,
  PRIMARY KEY (`id`),
  KEY `permission_doc_user_uuid` (`user_uuid`),
  KEY `permission_doc_doc_uuid` (`doc_uuid`),
  UNIQUE KEY `permission_doc_user_uuid_doc_uuid_idx` (`user_uuid`,`doc_uuid`),
  CONSTRAINT `permission_doc_user_uuid` FOREIGN KEY (`user_uuid`) REFERENCES `stroom_user` (`uuid`),
  CONSTRAINT `permission_doc_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permission_doc_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Copy permission values to the new table.
--
INSERT INTO permission_doc (user_uuid, doc_uuid, permission_id)
SELECT dp.user_uuid, dp.doc_uuid, MAX(pdi.id)
FROM doc_permission dp
JOIN permission_doc_id pdi
ON (pdi.permission = dp.permission)
WHERE dp.permission IN ("Owner", "Delete", "Update", "Read", "Use")
GROUP BY dp.user_uuid, dp.doc_uuid;

--
-- Modify the permission names.
--
UPDATE `permission_doc_id`
SET `permission` = "View"
WHERE `permission` = "Read";

UPDATE `permission_doc_id`
SET `permission` = "Edit"
WHERE `permission` = "Update";

--
-- Create the document type id table
--
CREATE TABLE IF NOT EXISTS `permission_doc_type_id` (
  `id` tinyint UNSIGNED NOT NULL AUTO_INCREMENT,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_doc_type_id_type_idx` (`type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Add document type names into the doc type id table.
--
INSERT INTO `permission_doc_type_id` (`type`)
SELECT DISTINCT(SUBSTRING(permission, 10))
FROM doc_permission
WHERE permission LIKE "Create - %";

--
-- Create the new document create permission table.
--
CREATE TABLE IF NOT EXISTS `permission_doc_create` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_uuid` varchar(255) NOT NULL,
  `doc_uuid` varchar(255) NOT NULL,
  `doc_type_id` tinyint UNSIGNED NOT NULL,
  PRIMARY KEY (`id`),
  KEY `permission_doc_create_user_uuid` (`user_uuid`),
  KEY `permission_doc_create_doc_uuid` (`doc_uuid`),
  KEY `permission_doc_create_doc_type_id` (`doc_type_id`),
  UNIQUE KEY `permission_doc_create_user_uuid_doc_uuid_doc_type_id_idx` (`user_uuid`,`doc_uuid`, `doc_type_id`),
  CONSTRAINT `permission_doc_create_user_uuid` FOREIGN KEY (`user_uuid`) REFERENCES `stroom_user` (`uuid`),
  CONSTRAINT `permission_doc_create_doc_type_id` FOREIGN KEY (`doc_type_id`) REFERENCES `permission_doc_type_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--
-- Copy document create permission values to the new table.
--
INSERT INTO permission_doc_create (user_uuid, doc_uuid, doc_type_id)
SELECT dp.user_uuid, dp.doc_uuid, pdti.id
FROM doc_permission dp
JOIN permission_doc_type_id pdti
ON (pdti.type = SUBSTRING(dp.permission, 10))
WHERE dp.permission LIKE "Create - %";

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
