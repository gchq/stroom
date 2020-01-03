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

CREATE TABLE IF NOT EXISTS index_volume_group (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  -- 'name' needs to be unique because it is used as a reference by IndexDoc.
  -- IndexDoc uses this name because it is fully portable -- if an index is imported
  -- then as long as it has the right index volume group name and the group exists
  -- it will use that index volume group. This would not be the case if the
  -- reference was a database generated ID or a uuid.
  UNIQUE (name),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS index_volume (
  id                        int(11) NOT NULL AUTO_INCREMENT,
  version                   int(11) NOT NULL,
  create_time_ms            bigint(20) NOT NULL,
  create_user               varchar(255) NOT NULL,
  update_time_ms            bigint(20) NOT NULL,
  update_user               varchar(255) NOT NULL,
  node_name                 varchar(255) DEFAULT NULL,
  path                      varchar(255) DEFAULT NULL,
  index_volume_group_name   varchar(255) NOT NULL,
  state                     tinyint(4) DEFAULT NULL,
  bytes_limit               bigint(20) DEFAULT NULL,
  bytes_used                bigint(20) DEFAULT NULL,
  bytes_free                bigint(20) DEFAULT NULL,
  bytes_total               bigint(20) DEFAULT NULL,
  status_ms                 bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY node_name_path (
      index_volume_group_name,
      node_name,
      path),
  -- The FK between index volume and index volume group is the name. The name can change, so we need to explicitly allow
  -- cascading. For an update we'll just cascade the the change down, for a delete we'll delete the index volume.
  CONSTRAINT index_volume_group_link_fk_group_name
      FOREIGN KEY (index_volume_group_name)
      REFERENCES index_volume_group (name)
      ON UPDATE CASCADE
      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET SQL_NOTES=@OLD_SQL_NOTES;
