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

CREATE TABLE meta_numeric_value (
  id 				        bigint(20) NOT NULL AUTO_INCREMENT,
  create_time               bigint(20) NOT NULL,
  stream_id                 bigint(20) NOT NULL,
  meta_key_id               int(11) NOT NULL,
  val 		                bigint(20) NOT NULL,
  PRIMARY KEY               (id),
  KEY                       meta_numeric_value_create_time_idx (create_time),
  KEY                       meta_numeric_value_stream_id_idx (stream_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

RENAME TABLE STRM_ATR_VAL TO OLD_STRM_ATR_VAL;
INSERT INTO meta_numeric_value (id, create_time, stream_id, meta_key_id, val)
SELECT o.ID, o.CRT_MS, o.STRM_ID, o.STRM_ATR_KEY_ID, o.VAL_NUM FROM OLD_STRM_ATR_VAL o WHERE o.VAL_NUM IS NOT NULL AND o.STRM_ATR_KEY_ID IS NOT NULL;



--ALTER TABLE STRM_ATR_KEY DROP COLUMN VER;
--ALTER TABLE STRM_ATR_KEY DROP COLUMN CRT_USER;
--ALTER TABLE STRM_ATR_KEY DROP COLUMN UPD_USER;
--ALTER TABLE STRM_ATR_KEY DROP COLUMN CRT_MS;
--ALTER TABLE STRM_ATR_KEY DROP COLUMN UPD_MS;
--
--RENAME TABLE STRM_ATR_KEY TO meta_key;
--ALTER TABLE meta_key CHANGE ID id int(11) NOT NULL AUTO_INCREMENT;
--ALTER TABLE meta_key CHANGE NAME name varchar(100) NOT NULL;
--ALTER TABLE meta_key CHANGE FLD_TP field_type tinyint(4) NOT NULL;
--
--ALTER TABLE STRM_ATR_VAL DROP COLUMN VER;
--ALTER TABLE STRM_ATR_VAL DROP COLUMN VAL_STR;
--
--DELETE FROM STRM_ATR_VAL WHERE VAL_NUM IS NULL OR STRM_ATR_KEY_ID IS NULL;
--RENAME TABLE STRM_ATR_VAL TO stream_attribute_value;
--ALTER TABLE stream_attribute_value CHANGE ID id bigint(20) NOT NULL AUTO_INCREMENT;
--ALTER TABLE stream_attribute_value CHANGE CRT_MS create_time bigint(20) NOT NULL;
--ALTER TABLE stream_attribute_value CHANGE VAL_NUM val bigint(20) NOT NULL;
--ALTER TABLE stream_attribute_value CHANGE STRM_ID stream_id bigint(20) NOT NULL;
--ALTER TABLE stream_attribute_value CHANGE STRM_ATR_KEY_ID meta_key_id int(11) NOT NULL;
--








