-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

DELIMITER $$

DROP PROCEDURE IF EXISTS core_run_sql_v1 $$

DROP PROCEDURE IF EXISTS core_create_non_unique_index_v1 $$

DROP PROCEDURE IF EXISTS core_add_column_v1 $$

DROP PROCEDURE IF EXISTS core_drop_column_v1 $$

DROP PROCEDURE IF EXISTS core_rename_column_v1 $$

DROP PROCEDURE IF EXISTS core_drop_constraint_v1 $$

DELIMITER ;

SET SQL_NOTES=@OLD_SQL_NOTES;
