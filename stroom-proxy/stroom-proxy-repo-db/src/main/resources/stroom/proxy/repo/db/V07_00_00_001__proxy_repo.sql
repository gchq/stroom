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
  feed_name                 VARCHAR(255),
  type_name                 VARCHAR(255) DEFAULT NULL,
  fk_zip_source_id          INTEGER NOT NULL,
  has_dest                  BOOLEAN,
  UNIQUE                    (name, fk_zip_source_id),
  FOREIGN KEY               (fk_zip_source_id) REFERENCES zip_source (id)
);

CREATE TABLE IF NOT EXISTS zip_entry (
  id                        INTEGER PRIMARY KEY,
  extension                 VARCHAR(255) NOT NULL,
  extension_type            INTEGER NOT NULL,
  byte_size                 BIGINT,
  fk_zip_data_id            INTEGER NOT NULL,
  FOREIGN KEY               (fk_zip_data_id) REFERENCES zip_data (id)
);

CREATE TABLE IF NOT EXISTS zip_dest (
  id                        INTEGER PRIMARY KEY,
  create_time_ms            BIGINT NOT NULL,
  feed_name                 VARCHAR(255) DEFAULT NULL,
  type_name                 VARCHAR(255) DEFAULT NULL,
  byte_size                 BIGINT NOT NULL,
  items                     INTEGER NOT NULL,
  complete                  BOOLEAN
);

CREATE TABLE IF NOT EXISTS zip_dest_data (
  id                        INTEGER PRIMARY KEY,
  fk_zip_dest_id            INTEGER,
  fk_zip_data_id            INTEGER,
  FOREIGN KEY               (fk_zip_dest_id) REFERENCES zip_dest (id),
  FOREIGN KEY               (fk_zip_data_id) REFERENCES zip_data (id)
);

CREATE TABLE IF NOT EXISTS forward_url (
  id                        INTEGER PRIMARY KEY,
  url                       VARCHAR(255) NOT NULL,
  UNIQUE                    (url)
);

CREATE TABLE IF NOT EXISTS forward_zip_dest (
  id                        INTEGER PRIMARY KEY,
  fk_forward_url_id         INTEGER,
  fk_zip_dest_id            INTEGER,
  success                   BOOLEAN,
  FOREIGN KEY               (fk_forward_url_id) REFERENCES forward_url (id),
  FOREIGN KEY               (fk_zip_dest_id) REFERENCES zip_dest (id)
);

-- vim: set shiftwidth=4 tabstop=4 expandtab:
