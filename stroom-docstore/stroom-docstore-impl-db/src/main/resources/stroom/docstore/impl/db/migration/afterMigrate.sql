-- stop note level warnings about objects (not)? existing
set @old_sql_notes=@@sql_notes, sql_notes=0;

drop procedure if exists create_docstore_index;


SET SQL_NOTES=@OLD_SQL_NOTES;
