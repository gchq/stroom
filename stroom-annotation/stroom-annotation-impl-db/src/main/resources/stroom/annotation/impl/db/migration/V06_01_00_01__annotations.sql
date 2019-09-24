--
-- Create the annotation tables
--
CREATE TABLE IF NOT EXISTS annotation (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  meta_id               bigint(20) NOT NULL,
  event_id              bigint(20) NOT NULL,
  current_status        varchar(255) NOT NULL,
  current_assignee      varchar(255) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            meta_id_event_id (meta_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS annotation_entry (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  fk_annotation_id      int(11) NOT NULL,
  type                  int(11) NOT NULL,
  data                  longtext,
  PRIMARY KEY           (id),
  CONSTRAINT            annotation_entry_fk_annotation_id FOREIGN KEY (fk_annotation_id) REFERENCES annotation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;