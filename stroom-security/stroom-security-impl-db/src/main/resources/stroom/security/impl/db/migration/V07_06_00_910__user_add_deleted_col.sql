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

-- An archive of the last known values of name, display_name, full_name and is_group for a given
-- uuid. No constraint on name to allow for stroom_user records being deleted and re-used with
-- a different uuid.
CREATE TABLE IF NOT EXISTS `stroom_user_archive` (
  `id` int NOT NULL AUTO_INCREMENT,
  `uuid` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `display_name` varchar(255) NOT NULL,
  `full_name` varchar(255) DEFAULT NULL,
  `is_group` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `stroom_user_archive_uuid_idx` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Idempotent
-- Populate the new table based on what we currently have in the stroom_user table
INSERT INTO stroom_user_archive (
    uuid,
    name,
    display_name,
    full_name,
    is_group)
SELECT
    su.uuid,
    su.name,
    su.display_name,
    su.full_name,
    su.is_group
FROM stroom_user su
ON DUPLICATE KEY UPDATE
    uuid = su.uuid,
    name = su.name,
    display_name = su.display_name,
    full_name = su.full_name,
    is_group = su.is_group;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
