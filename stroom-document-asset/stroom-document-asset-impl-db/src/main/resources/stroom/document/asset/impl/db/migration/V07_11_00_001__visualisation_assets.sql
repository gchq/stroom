-- ------------------------------------------------------------------------
-- Copyright 2025 Crown Copyright
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
-- Create the Visualisation Assets table.
-- Note: to avoid max key length issues, the path is hashed for use in
-- indexes.
--
CREATE TABLE IF NOT EXISTS visualisation_assets (
  id                    int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  modified              bigint NOT NULL,
  owner_doc_uuid        varchar(255) NOT NULL,
  asset_uuid            varchar(255) NOT NULL,
  path                  varchar(512) NOT NULL,
  path_hash             binary(32) NOT NULL,
  is_folder             bool NOT NULL,
  data                  longblob NULL,
  CONSTRAINT k_visualisation_assets UNIQUE KEY (owner_doc_uuid, path_hash),
  CONSTRAINT k_asset_uuid UNIQUE KEY (asset_uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Stores interim or draft saves, before updating the main table
-- when the user clicks save.
--
CREATE TABLE IF NOT EXISTS visualisation_assets_draft (
  id                    INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  draft_user_uuid       varchar(255) NOT NULL,
  owner_doc_uuid        varchar(255) NOT NULL,
  asset_uuid            varchar(255) NOT NULL,
  path                  varchar(512) NOT NULL,
  path_hash             binary(32) NOT NULL,
  is_folder             bool NOT NULL,
  data                  longblob NULL,
  CONSTRAINT k_visualisation_assets_draft UNIQUE KEY (draft_user_uuid, owner_doc_uuid, path_hash),
  CONSTRAINT k_asset_uuid UNIQUE KEY (asset_uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- A row is created in this table when the last operation is delete.
-- If this row exists then it is ok to copy from an empty draft table
-- to the main table. Otherwise it might be a bug in the UI that allows
-- two saves with no updates between them.
--
CREATE TABLE IF NOT EXISTS visualisation_assets_update_delete (
  id                    INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  draft_user_uuid       varchar(255) NOT NULL,
  owner_doc_uuid        varchar(255) NOT NULL,
  CONSTRAINT k_visualisation_assets_draft UNIQUE KEY (draft_user_uuid, owner_doc_uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;


-- vim: set shiftwidth=4 tabstop=4 expandtab:
