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

CREATE TABLE IF NOT EXISTS `api_key` (
  id                int NOT NULL AUTO_INCREMENT,
  version           int NOT NULL,
  create_time_ms    bigint NOT NULL,
  create_user       varchar(255) NOT NULL,
  update_time_ms    bigint NOT NULL,
  update_user       varchar(255) NOT NULL,
  fk_owner_uuid     varchar(255) NOT NULL,
  api_key           varchar(255) NOT NULL,
  expires_on_ms     bigint DEFAULT NULL,
  name              varchar(255) NOT NULL,
  comments          longtext,
  enabled           tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `api_key_api_key_idx` (`api_key`),
  UNIQUE KEY `api_key_owner_name_idx` (`fk_owner_uuid`, `name`),
  CONSTRAINT api_key_fk_owner_uuid
    FOREIGN KEY (fk_owner_uuid)
    REFERENCES stroom_user (uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
