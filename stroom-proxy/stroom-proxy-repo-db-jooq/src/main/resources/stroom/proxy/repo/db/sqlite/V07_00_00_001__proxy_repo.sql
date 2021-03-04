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

CREATE TABLE IF NOT EXISTS source (
  id                        BIGINT PRIMARY KEY,
  path                      VARCHAR(255) NOT NULL UNIQUE,
  last_modified_time_ms     BIGINT NOT NULL,
  examined                  BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS source_item (
  id                        BIGINT PRIMARY KEY,
  number                    INTEGER NOT NULL,
  name                      VARCHAR(255) NOT NULL,
  feed_name                 VARCHAR(255) DEFAULT NULL,
  type_name                 VARCHAR(255) DEFAULT NULL,
  fk_source_id              BIGINT NOT NULL,
  aggregated                BOOLEAN DEFAULT FALSE,
  UNIQUE                    (name, fk_source_id),
  FOREIGN KEY               (fk_source_id) REFERENCES source (id)
);

CREATE TABLE IF NOT EXISTS source_entry (
  id                        BIGINT PRIMARY KEY,
  extension                 VARCHAR(255) NOT NULL,
  extension_type            INTEGER NOT NULL,
  byte_size                 BIGINT DEFAULT 0,
  fk_source_item_id         BIGINT NOT NULL,
  FOREIGN KEY               (fk_source_item_id) REFERENCES source_item (id)
);

CREATE TABLE IF NOT EXISTS aggregate (
  id                        BIGINT PRIMARY KEY,
  create_time_ms            BIGINT NOT NULL,
  feed_name                 VARCHAR(255) DEFAULT NULL,
  type_name                 VARCHAR(255) DEFAULT NULL,
  byte_size                 BIGINT NOT NULL,
  items                     INTEGER NOT NULL,
  complete                  BOOLEAN DEFAULT FALSE,
  forward_error             BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS aggregate_item (
  id                        BIGINT PRIMARY KEY,
  fk_aggregate_id           BIGINT NOT NULL,
  fk_source_item_id         BIGINT NOT NULL,
  FOREIGN KEY               (fk_aggregate_id) REFERENCES aggregate (id),
  FOREIGN KEY               (fk_source_item_id) REFERENCES source_item (id)
);

CREATE TABLE IF NOT EXISTS forward_url (
  id                        INTEGER PRIMARY KEY,
  url                       VARCHAR(255) NOT NULL,
  UNIQUE                    (url)
);

CREATE TABLE IF NOT EXISTS forward_aggregate (
  id                        BIGINT PRIMARY KEY,
  fk_forward_url_id         INTEGER NOT NULL,
  fk_aggregate_id           BIGINT NOT NULL,
  success                   BOOLEAN NOT NULL,
  error                     VARCHAR(255),
  FOREIGN KEY               (fk_forward_url_id) REFERENCES forward_url (id),
  FOREIGN KEY               (fk_aggregate_id) REFERENCES aggregate (id)
);

-- vim: set shiftwidth=4 tabstop=4 expandtab:
