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

CREATE TABLE IF NOT EXISTS zip_source (
  id                        INTEGER PRIMARY KEY,
  path                      VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS zip_data (
  id                        INTEGER PRIMARY KEY,
  name                      VARCHAR(255) NOT NULL,
  feedName                  VARCHAR(255),
  fk_zip_source_id          INTEGER NOT NULL,
  has_dest                  BOOLEAN,
  UNIQUE                    (name, fk_zip_source_id),
  FOREIGN KEY               (fk_zip_source_id) REFERENCES zip_source (id)
);

CREATE TABLE IF NOT EXISTS zip_entry (
  id                        INTEGER PRIMARY KEY,
  extension                 VARCHAR(255) NOT NULL,
  byte_size                 BIGINT,
  fk_zip_data_id            INTEGER NOT NULL,
  FOREIGN KEY               (fk_zip_data_id) REFERENCES zip_data (id)
);

CREATE TABLE IF NOT EXISTS zip_dest (
  id                        INTEGER PRIMARY KEY,
  create_time_ms            BIGINT NOT NULL,
  feedName                  VARCHAR(255) DEFAULT NULL,
  byte_size                 BIGINT,
  in_use                    BOOLEAN,
  complete                  BOOLEAN
);

CREATE TABLE IF NOT EXISTS zip_dest_data (
  id                        INTEGER PRIMARY KEY,
  fk_zip_dest_id            INTEGER,
  fk_zip_data_id            INTEGER,
  FOREIGN KEY               (fk_zip_dest_id) REFERENCES zip_dest (id),
  FOREIGN KEY               (fk_zip_data_id) REFERENCES zip_data (id)
);

-- vim: set shiftwidth=4 tabstop=4 expandtab:
