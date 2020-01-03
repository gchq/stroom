-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

DROP PROCEDURE IF EXISTS create_statistics_index;

DELIMITER $$
CREATE PROCEDURE create_statistics_index
(
    given_database varchar(64),
    given_table varchar(64),
    given_index varchar(64),
    is_unique_index boolean,
    given_columns varchar(64)
)
BEGIN
    DECLARE index_count integer;
    DECLARE unique_modifier varchar(20);

    IF (is_unique_index = 'T') THEN
        SET unique_modifier = 'unique ';
    ELSE
        SET unique_modifier = '';
    END IF;

    SELECT COUNT(1)
    INTO index_count
    FROM information_schema.statistics
    WHERE table_schema = given_database
    AND table_name = given_table
    AND index_name = given_index;

    IF index_count = 0 THEN
        SET @sqlstmt = CONCAT(
            'create ', unique_modifier, 'index ', given_index,
            ' on ', given_database, '.', given_table,
            ' (', given_columns, ')');

        PREPARE stmt FROM @sqlstmt;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSE
        SELECT CONCAT(
            'index ',
            given_index,
            ' already exists on table ',
            given_database,
            '.',
            given_table);
    END IF;
END $$
DELIMITER ;

SET SQL_NOTES=@OLD_SQL_NOTES;
