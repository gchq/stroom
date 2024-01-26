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
  fk_owner_uuid     varchar(255) NOT NULL, -- stroom user owner of the key
  api_key_hash      varchar(255) NOT NULL, -- sha256 hash of the whole api key
  api_key_prefix    varchar(255) NOT NULL, -- the key type and the 10 char checksum, e.g. sak_baf937d27f_, allows user to identify key
  expires_on_ms     bigint DEFAULT NULL,   -- when the key expires
  name              varchar(255) NOT NULL, -- so the user can give a sensible name to their key
  comments          longtext,              -- any user comments about the key
  enabled           tinyint NOT NULL DEFAULT '0', -- allows the key to be disabled, preventing authentication
  PRIMARY KEY (`id`),
  UNIQUE KEY `api_key_api_key_hash_idx` (api_key_hash), -- we query by hash so don't want any clashes
  UNIQUE KEY `api_key_owner_name_idx` (`fk_owner_uuid`, `name`),
  UNIQUE KEY `api_key_prefix_idx` (api_key_prefix), -- if unique, users can be sure which key is which
  CONSTRAINT api_key_fk_owner_uuid
    FOREIGN KEY (fk_owner_uuid)
    REFERENCES stroom_user (uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
