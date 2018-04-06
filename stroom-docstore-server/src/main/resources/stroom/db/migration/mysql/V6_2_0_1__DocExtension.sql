ALTER TABLE doc MODIFY COLUMN data LONGBLOB;
ALTER TABLE doc DROP INDEX type;
ALTER TABLE doc ADD COLUMN extension varchar(255) DEFAULT NULL;
ALTER TABLE doc ADD CONSTRAINT type_uuid_extension UNIQUE (type, uuid, extension);
UPDATE doc SET extension = "meta";

CREATE INDEX doc_type_uuid_extension_idx ON doc (type, uuid, extension);