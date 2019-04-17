CREATE TABLE IF NOT EXISTS index_volume_group (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY index_volume_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS index_volume (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  node_name             varchar(255) DEFAULT NULL,
  path                  varchar(255) DEFAULT NULL,
  state                 tinyint(4) DEFAULT NULL,
  bytes_limit           bigint(20) DEFAULT NULL,
  bytes_used            bigint(20) DEFAULT NULL,
  bytes_free            bigint(20) DEFAULT NULL,
  bytes_total           bigint(20) DEFAULT NULL,
  status_ms             bigint(20) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY node_name_path (node_name, path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS index_volume_group_link (
  fk_index_volume_group_id  int(11) NOT NULL,
  fk_index_volume_id        int(11) NOT NULL,
  UNIQUE KEY index_volume_group_link_unique (fk_index_volume_group_id,fk_index_volume_id),
  CONSTRAINT index_volume_group_link_fk_group_id FOREIGN KEY (fk_index_volume_group_id) REFERENCES index_volume_group (id),
  CONSTRAINT index_volume_group_link_fk_volume_id FOREIGN KEY (fk_index_volume_id) REFERENCES index_volume (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;