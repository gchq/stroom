-- This script renames all the legacy auth tables to be prefixed with 'OLD_AUTH_'
-- This MUST be run on the auth database which should contain the following tables:
--   
--  json_web_key
--  token_types
--  tokens
--  users
-- 
-- Run with the mysql --table arg to get formatted output
-- e.g.
-- docker exec -i stroom-all-dbs mysql --table -h"localhost" -P"3307" -u"authuser" -p"stroompassword1" auth < v7_auth_db_table_rename.sql > v7_auth_db_table_rename.out

-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

\! echo 'Creating temporary stored procedure old_auth_run_sql';

DROP PROCEDURE IF EXISTS old_auth_run_sql;

DELIMITER $$

CREATE PROCEDURE old_auth_run_sql (
    p_sql_stmt varchar(1000)
)
BEGIN
    SET @sqlstmt = p_sql_stmt;

    SELECT CONCAT('Running sql: ', @sqlstmt);

    PREPARE stmt FROM @sqlstmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END $$

DELIMITER ;

\! echo 'Creating temporary stored procedure old_auth_rename_table_if_exists';

DROP PROCEDURE IF EXISTS old_auth_rename_table_if_exists;

DELIMITER $$

CREATE PROCEDURE old_auth_rename_table_if_exists (
    p_old_table_name varchar(64)
)
BEGIN
    DECLARE new_table_name varchar(64);
    DECLARE old_object_count integer;
    DECLARE new_object_count integer;

    SET new_table_name = CONCAT('OLD_AUTH_', p_old_table_name);

    SELECT COUNT(1)
    INTO old_object_count
    FROM information_schema.tables
    WHERE table_schema = database()
    AND UPPER(table_name) = UPPER(p_old_table_name);

    SELECT COUNT(1)
    INTO new_object_count
    FROM information_schema.tables
    WHERE table_schema = database()
    AND UPPER(table_name) = UPPER(new_table_name);

    IF (old_object_count = 1 AND new_object_count = 0) THEN
        CALL old_auth_run_sql(CONCAT(
            'rename table ', database(), '.', p_old_table_name,
            ' to ', new_table_name));
    ELSE
        IF (old_object_count = 0) THEN
            SELECT CONCAT(
                'Source table ',
                database(),
                '.',
                p_old_table_name,
                ' does not exist');
        END IF;

        IF (new_object_count = 1) THEN
            SELECT CONCAT(
                'Destination table ',
                database(),
                '.',
                new_table_name,
                ' already exists');
        END IF;
    END IF;
END $$

DELIMITER ;

\! echo 'Renaming legacy auth tables';

CALL old_auth_rename_table_if_exists('json_web_key');
CALL old_auth_rename_table_if_exists('schema_version');
CALL old_auth_rename_table_if_exists('token_types');
CALL old_auth_rename_table_if_exists('tokens');
CALL old_auth_rename_table_if_exists('users');

\! echo 'Dropping temporary stored procedure old_auth_run_sql';

DROP PROCEDURE IF EXISTS old_auth_run_sql;

\! echo 'Dropping temporary stored procedure old_auth_rename_table_if_exists';

DROP PROCEDURE IF EXISTS old_auth_rename_table_if_exists;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
