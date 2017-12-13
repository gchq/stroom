CREATE TABLE doc (
  id 		bigint(20) auto_increment PRIMARY KEY,
  type 		varchar(255) NOT NULL,
  uuid 		varchar(255) NOT NULL,
  name 		varchar(255) NOT NULL,
  data 		longtext,
  UNIQUE 	(type,uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE INDEX doc_type_uuid_idx ON doc (type, uuid);
CREATE INDEX doc_uuid_idx ON doc (uuid);