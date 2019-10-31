--
-- Create the annotation tables
--
CREATE TABLE IF NOT EXISTS annotation (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  stream_id             bigint(20) DEFAULT NULL,
  event_id              bigint(20) DEFAULT NULL,
  title                 longtext,
  subject               longtext,
  status                varchar(255) NOT NULL,
  assigned_to           varchar(255) DEFAULT NULL,
  comment               longtext,
  history               longtext,
  PRIMARY KEY           (id),
  UNIQUE KEY            stream_id_event_id (stream_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS annotation_entry (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  fk_annotation_id      bigint(20) NOT NULL,
  type                  varchar(255) NOT NULL,
  data                  longtext,
  PRIMARY KEY           (id),
  CONSTRAINT            annotation_entry_fk_annotation_id FOREIGN KEY (fk_annotation_id) REFERENCES annotation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;