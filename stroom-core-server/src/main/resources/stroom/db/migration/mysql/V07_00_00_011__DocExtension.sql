ALTER TABLE doc MODIFY COLUMN data LONGBLOB;
ALTER TABLE doc DROP INDEX type;
ALTER TABLE doc ADD COLUMN ext varchar(255) DEFAULT NULL;
ALTER TABLE doc ADD CONSTRAINT type_uuid_ext UNIQUE (type, uuid, ext);
UPDATE doc SET ext = "meta";

CREATE INDEX doc_type_uuid_ext_idx ON doc (type, uuid, ext);