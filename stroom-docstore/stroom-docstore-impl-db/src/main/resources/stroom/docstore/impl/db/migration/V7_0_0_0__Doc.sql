-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS create_docstore_index;

DELIMITER $$
CREATE PROCEDURE create_docstore_index
(
    given_database VARCHAR(64),
    given_table VARCHAR(64),
    given_index VARCHAR(64),
    is_unique_index BOOLEAN,
    given_columns VARCHAR(64)
)
BEGIN
    DECLARE does_index_exist INTEGER;
    DECLARE unique_modifier VARCHAR;

    if is_unique_index THEN
        SET @unique_modifier = 'UNIQUE ';
    ELSE
        SET @unique_modifier = '';
    END IF;

    SELECT COUNT(1)
    INTO does_index_exist
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = given_database
    AND table_name = given_table
    AND index_name = given_index;

    IF does_index_exist = 0 THEN
        SET @sqlstmt = CONCAT(
            'CREATE ', unique_modifier, 'INDEX ', given_index,
            ' ON ', given_database, '.', given_table,
            ' (', given_columns, ')');

        PREPARE st FROM @sqlstmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    ELSE
        SELECT CONCAT(
            'Index ',
            given_index,
            ' already exists on Table ',
            given_database,
            '.',
            given_table);
    END IF;
END $$
DELIMITER ;

CREATE TABLE IF NOT EXISTS doc (
  id 		bigint(20) auto_increment PRIMARY KEY,
  type 		varchar(255) NOT NULL,
  uuid 		varchar(255) NOT NULL,
  name 		varchar(255) NOT NULL,
  data 		longtext,
  UNIQUE 	(type, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

call create_docstore_index(database(),'doc','doc_type_uuid_idx',false,'type, uuid');
call create_docstore_index(database(),'doc','doc_uuid_idx',false,'uuid');

SET SQL_NOTES=@OLD_SQL_NOTES;
