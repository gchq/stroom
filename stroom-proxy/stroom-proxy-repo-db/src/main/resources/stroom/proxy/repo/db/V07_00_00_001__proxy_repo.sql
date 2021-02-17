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
  id                        bigint(20) NOT NULL AUTO_INCREMENT,
  path                      varchar(255) NOT NULL,
  PRIMARY KEY               (id),
  UNIQUE KEY                zip_source_path (path)
);

CREATE TABLE IF NOT EXISTS zip_data (
  id                        bigint(20) NOT NULL AUTO_INCREMENT,
  name                      varchar(255) NOT NULL,
  feedName                  varchar(255) DEFAULT NULL,
  fk_zip_source_id          bigint(20) NOT NULL,
  PRIMARY KEY               (id),
  UNIQUE KEY                zip_data_name (name, fk_zip_source_id),
  CONSTRAINT zip_data_fk_zip_source_id FOREIGN KEY (fk_zip_source_id) REFERENCES zip_source (id)
);

CREATE TABLE IF NOT EXISTS zip_entry (
  id                        bigint(20) NOT NULL AUTO_INCREMENT,
  extension                 varchar(255) NOT NULL,
  byte_size                 bigint(20) DEFAULT NULL,
  fk_zip_source_id          bigint(20) NOT NULL,
  fk_zip_data_id            bigint(20) NOT NULL,
  PRIMARY KEY               (id),
  UNIQUE KEY                zip_entry_extension (fk_zip_source_id, fk_zip_data_id, extension),
  CONSTRAINT zip_entry_fk_zip_source_id FOREIGN KEY (fk_zip_source_id) REFERENCES zip_source (id),
  CONSTRAINT zip_entry_fk_zip_data_id FOREIGN KEY (fk_zip_data_id) REFERENCES zip_data (id)
);

CREATE TABLE IF NOT EXISTS zip_dest (
  id                        bigint(20) NOT NULL AUTO_INCREMENT,
  feedName                  varchar(255) DEFAULT NULL,
  byte_size                 bigint(20) DEFAULT NULL,
  PRIMARY KEY               (id)
);

CREATE TABLE IF NOT EXISTS zip_dest_entry (
  id                        bigint(20) NOT NULL AUTO_INCREMENT,
  fk_zip_dest_id            bigint(20) DEFAULT NULL,
  fk_zip_entry_id           bigint(20) DEFAULT NULL,
  PRIMARY KEY               (id),
  CONSTRAINT zip_dest_entry_fk_zip_dest_id FOREIGN KEY (fk_zip_dest_id) REFERENCES zip_dest (id),
  CONSTRAINT zip_dest_entry_fk_zip_entry_id FOREIGN KEY (fk_zip_entry_id) REFERENCES zip_entry (id),
);

-- vim: set shiftwidth=4 tabstop=4 expandtab:
