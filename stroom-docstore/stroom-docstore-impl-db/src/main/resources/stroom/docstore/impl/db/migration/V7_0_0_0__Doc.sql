-- stop note level warnings about objects (not)? existing
set @old_sql_notes=@@sql_notes, sql_notes=0;

CREATE TABLE IF NOT EXISTS doc (
  id 		bigint(20) auto_increment PRIMARY KEY,
  type 		varchar(255) NOT NULL,
  uuid 		varchar(255) NOT NULL,
  name 		varchar(255) NOT NULL,
  data 		longtext,
  UNIQUE 	(type, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

call create_docstore_index(database(), 'doc', 'doc_type_uuid_idx', false, 'type, uuid');
call create_docstore_index(database(), 'doc', 'doc_uuid_idx', false, 'uuid');

SET SQL_NOTES=@OLD_SQL_NOTES;
