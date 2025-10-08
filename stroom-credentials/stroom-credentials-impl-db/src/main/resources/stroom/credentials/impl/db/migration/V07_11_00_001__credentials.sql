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
-- Create the credentials tables
--
CREATE TABLE IF NOT EXISTS credentials (
  uuid         varchar(255) NOT NULL,
  name         varchar(255) NOT NULL,
  type         varchar(255) NOT NULL,
  credsExpire  bool NOT NULL,
  expires      bigint NOT NULL,
  PRIMARY KEY  (uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS credentials_secret (
  uuid         varchar(255) NOT NULL,
  secret       longtext NOT NULL,
  PRIMARY KEY (uuid)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
