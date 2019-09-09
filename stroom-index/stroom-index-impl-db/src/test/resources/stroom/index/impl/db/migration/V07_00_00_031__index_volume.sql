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
  UNIQUE KEY node_name_path (index_volume_group_name, node_name, path),
  CONSTRAINT index_volume_group_link_fk_group_name FOREIGN KEY (index_volume_group_name) REFERENCES index_volume_group (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
