CREATE TABLE meta_key (
  id 				int(11) NOT NULL AUTO_INCREMENT,
  name 		        varchar(100) NOT NULL,
  field_type 		tinyint(4) NOT NULL,
  PRIMARY KEY       (id),
  UNIQUE KEY		name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


RENAME TABLE STRM_ATR_KEY TO OLD_STRM_ATR_KEY;
INSERT INTO meta_key (id, name, field_type)
SELECT OLD_STRM_ATR_KEY.ID, OLD_STRM_ATR_KEY.NAME, OLD_STRM_ATR_KEY.FLD_TP FROM OLD_STRM_ATR_KEY;

CREATE TABLE meta_val (
  id 				        bigint(20) NOT NULL AUTO_INCREMENT,
  create_time               bigint(20) NOT NULL,
  data_id                   bigint(20) NOT NULL,
  meta_key_id               int(11) NOT NULL,
  val 		                bigint(20) NOT NULL,
  PRIMARY KEY               (id),
  KEY                       meta_value_create_time_idx (create_time),
  KEY                       meta_value_data_id_idx (data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

RENAME TABLE STRM_ATR_VAL TO OLD_STRM_ATR_VAL;
INSERT INTO meta_val (id, create_time, data_id, meta_key_id, val)
SELECT o.ID, o.CRT_MS, o.STRM_ID, o.STRM_ATR_KEY_ID, o.VAL_NUM FROM OLD_STRM_ATR_VAL o WHERE o.VAL_NUM IS NOT NULL AND o.STRM_ATR_KEY_ID IS NOT NULL;
