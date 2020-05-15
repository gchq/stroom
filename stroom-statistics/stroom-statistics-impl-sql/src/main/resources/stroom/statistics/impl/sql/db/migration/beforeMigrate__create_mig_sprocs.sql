-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

--    ****** IMPORTANT ******

-- These stored procedures are intended to be used in the migration
-- scripts to improve code re-use. Each stored proc many be used by multiple
-- migration scripts so you need to be mindful if the impact of changes in this
-- file on ALL of the scripts that use it. As this file is run as a callback
-- it is not checksumed. If you need to make a breaking change to a stored proc
-- then copy it and increment its version number.

-- Any stored procs created here should be dropped in the corresponding
-- afterMigrate__ file.

-- Stored proc names are namespaced to avoid any clashes
-- when we have multiple flyway modules on the same DB.

--    ****** IMPORTANT ******

DELIMITER $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_run_sql_v1 $$

CREATE PROCEDURE statistics_run_sql_v1(
    p_sql_stmt varchar(1000)
)
BEGIN

    SET @sqlstmt = p_sql_stmt;

    SELECT CONCAT('Running sql: ', @sqlstmt);

    PREPARE stmt FROM @sqlstmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_create_unique_index_v1$$

CREATE PROCEDURE statistics_create_unique_index_v1 (
    p_table_name varchar(64),
    p_index_name varchar(64),
    p_index_columns varchar(64)
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND index_name = p_index_name;

    IF object_count = 0 THEN
        CALL statistics_run_sql_v1(CONCAT(
            'create unique index ', p_index_name,
            ' on ', database(), '.', p_table_name,
            ' (', p_index_columns, ')'));
    ELSE
        SELECT CONCAT(
            'Index ',
            p_index_name,
            ' already exists on table ',
            database(),
            '.',
            p_table_name);
    END IF;
END $$


-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_create_non_unique_index_v1$$

CREATE PROCEDURE statistics_create_non_unique_index_v1 (
    p_table_name varchar(64),
    p_index_name varchar(64),
    p_index_columns varchar(64)
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND index_name = p_index_name;

    IF object_count = 0 THEN
        CALL statistics_run_sql_v1(CONCAT(
            'create index ', p_index_name,
            ' on ', database(), '.', p_table_name,
            ' (', p_index_columns, ')'));
    ELSE
        SELECT CONCAT(
            'Index ',
            p_index_name,
            ' already exists on table ',
            database(),
            '.',
            p_table_name);
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_add_column_v1$$

CREATE PROCEDURE statistics_add_column_v1 (
    p_table_name varchar(64),
    p_column_name varchar(64),
    p_column_type_info varchar(64) -- e.g. 'varchar(255) default NULL'
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND column_name = p_column_name;

    IF object_count = 0 THEN
        CALL statistics_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' add column ', p_column_name, ' ', p_column_type_info));
    ELSE
        SELECT CONCAT(
            'Column ',
            p_column_name,
            ' already exists on table ',
            database(),
            '.',
            p_table_name);
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_drop_column_v1$$

CREATE PROCEDURE statistics_drop_column_v1 (
    p_table_name varchar(64),
    p_column_name varchar(64)
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND column_name = p_column_name;

    IF object_count = 0 THEN
        SELECT CONCAT(
            'Column ',
            p_column_name,
            ' does not exist on table ',
            database(),
            '.',
            p_table_name);
    ELSE
        CALL statistics_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop column ', p_column_name));
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_rename_column_v1$$

CREATE PROCEDURE statistics_rename_column_v1 (
    p_table_name varchar(64),
    p_old_column_name varchar(64),
    p_new_column_name varchar(64),
    p_column_type_info varchar(64) -- e.g. 'varchar(255) default NULL'
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND column_name = p_new_column_name;

    IF object_count = 0 THEN
        CALL statistics_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' change ', p_old_column_name, ' ',
            p_new_column_name, ' ', p_column_type_info));
    ELSE
        SELECT CONCAT(
            'Column ',
            p_new_column_name,
            ' already exists on table ',
            database(),
            '.',
            p_table_name);
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS statistics_drop_constraint_v1 $$

-- e.g. statistics_drop_constraint_v1('MY_TABLE', 'MY_FK', 'FOREIGN KEY');
--      statistics_drop_constraint_v1('MY_TABLE', 'MY_UNIQ_IDX', 'INDEX');
--      statistics_drop_constraint_v1('MY_TABLE', 'PRIMARY', 'INDEX');
CREATE PROCEDURE statistics_drop_constraint_v1 (
    p_table_name varchar(64),
    p_constraint_name varchar(64),
    p_constraint_type varchar(64) -- e.g. FOREIGN KEY | UNIQUE
)
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.table_constraints
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND constraint_name = p_constraint_name;

    IF object_count = 0 THEN
        SELECT CONCAT(
            'Constraint ',
            p_constraint_name,
            ' does not exist on table ',
            database(),
            '.',
            p_table_name);
    ELSE
        CALL statistics_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop ', p_constraint_type, ' ', p_constraint_name));
    END IF;
END $$

DELIMITER ;

SET SQL_NOTES=@OLD_SQL_NOTES;
