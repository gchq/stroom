DELIMITER $$

DROP PROCEDURE IF EXISTS `CreateIndex` $$
CREATE PROCEDURE `CreateIndex`
(
    given_database VARCHAR(64),
    given_table    VARCHAR(64),
    given_index    VARCHAR(64),
    given_columns  VARCHAR(64)
)
BEGIN

    DECLARE IndexIsThere INTEGER;

    SELECT COUNT(1) INTO IndexIsThere
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = given_database
    AND   table_name   = given_table
    AND   index_name   = given_index;

    IF IndexIsThere = 0 THEN
        SET @sqlstmt = CONCAT('CREATE INDEX ',given_index,' ON ',
        given_database,'.',given_table,' (',given_columns,')');
        PREPARE st FROM @sqlstmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    ELSE
        SELECT CONCAT('Index ',given_index,' already exists on Table ',
        given_database,'.',given_table) CreateindexErrorMessage;
    END IF;

END $$

DELIMITER ;

CREATE TABLE IF NOT EXISTS doc (
  id 		bigint(20) auto_increment PRIMARY KEY,
  type 		varchar(255) NOT NULL,
  uuid 		varchar(255) NOT NULL,
  name 		varchar(255) NOT NULL,
  data 		longtext,
  UNIQUE 	(type,uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

call createindex(database(),'doc','doc_type_uuid_idx','type, uuid');
call createindex(database(),'doc','doc_uuid_idx','uuid');