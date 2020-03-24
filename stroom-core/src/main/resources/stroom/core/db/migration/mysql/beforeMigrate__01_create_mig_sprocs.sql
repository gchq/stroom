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

DROP PROCEDURE IF EXISTS core_run_sql_v1 $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_run_sql_v1 (
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

DROP PROCEDURE IF EXISTS core_create_index_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_create_index_v1 (
    p_table_name varchar(64),
    p_index_name varchar(64),
    p_is_unique_index boolean,
    p_index_columns varchar(64)
)
BEGIN
    DECLARE object_count integer;
    DECLARE unique_modifier varchar(20);

    IF (p_is_unique_index = 'T') THEN
        SET unique_modifier = 'unique ';
    ELSE
        SET unique_modifier = '';
    END IF;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = p_table_name
    AND index_name = p_index_name;

    IF object_count = 0 THEN
        CALL core_run_sql_v1(CONCAT(
            'create ', unique_modifier, 'index ', p_index_name,
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

DROP PROCEDURE IF EXISTS core_add_column_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_add_column_v1 (
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
        CALL core_run_sql_v1(CONCAT(
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

DROP PROCEDURE IF EXISTS core_drop_column_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_drop_column_v1 (
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
        CALL core_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop column ', p_column_name));
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS core_rename_column_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_rename_column_v1 (
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
        CALL core_run_sql_v1(CONCAT(
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

DROP PROCEDURE IF EXISTS core_drop_constraint_v1 $$

-- e.g. core_drop_constraint_v1('MY_TABLE', 'MY_FK', 'FOREIGN KEY');
--      core_drop_constraint_v1('MY_TABLE', 'MY_UNIQ_IDX', 'INDEX');
--      core_drop_constraint_v1('MY_TABLE', 'PRIMARY', 'INDEX');
-- DO NOT change this without reading the header!
CREATE PROCEDURE core_drop_constraint_v1 (
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
        CALL core_run_sql_v1(CONCAT(
            'alter table ', database(), '.', p_table_name,
            ' drop ', p_constraint_type, ' ', p_constraint_name));
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS core_rename_table_if_exists_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_rename_table_if_exists_v1 (
    p_old_table_name varchar(64),
    p_new_table_name varchar(64)
)
BEGIN
    DECLARE old_object_count integer;
    DECLARE new_object_count integer;

    SELECT COUNT(1)
    INTO old_object_count
    FROM information_schema.tables
    WHERE table_schema = database()
    AND UPPER(table_name) = UPPER(p_old_table_name);

    SELECT COUNT(1)
    INTO new_object_count
    FROM information_schema.tables
    WHERE table_schema = database()
    AND UPPER(table_name) = UPPER(p_new_table_name);

    IF (old_object_count = 1 AND new_object_count = 0) THEN
        CALL core_run_sql_v1(CONCAT(
            'rename table ', database(), '.', p_old_table_name,
            ' to ', p_new_table_name));
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
                p_new_table_name,
                ' already exists');
        END IF;
    END IF;
END $$

-- --------------------------------------------------

DROP PROCEDURE IF EXISTS core_rename_legacy_table_if_exists_v1$$

-- DO NOT change this without reading the header!
CREATE PROCEDURE core_rename_legacy_table_if_exists_v1 (
    p_old_table_name varchar(64)
)
BEGIN
    CALL core_rename_table_if_exists_v1(p_old_table_name, CONCAT('OLD_', p_old_table_name));
END $$

DELIMITER ;

SET SQL_NOTES=@OLD_SQL_NOTES;
