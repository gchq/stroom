-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

DROP PROCEDURE IF EXISTS create_statistics_index;

SET SQL_NOTES=@OLD_SQL_NOTES;
