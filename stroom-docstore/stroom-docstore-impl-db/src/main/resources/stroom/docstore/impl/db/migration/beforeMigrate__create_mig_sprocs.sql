-- stop note level warnings about objects (not)? existing
set @old_sql_notes=@@sql_notes, sql_notes=0;

drop procedure if exists create_docstore_index;

delimiter $$
create procedure create_docstore_index
(
    given_database varchar(64),
    given_table varchar(64),
    given_index varchar(64),
    is_unique_index boolean,
    given_columns varchar(64)
)
begin
    declare index_count integer;
    declare unique_modifier varchar(20);

    if (is_unique_index = 'T') then
        set unique_modifier = 'unique ';
    else
        set unique_modifier = '';
    end if;

    select count(1)
    into index_count
    from information_schema.statistics
    where table_schema = given_database
    and table_name = given_table
    and index_name = given_index;

    if index_count = 0 then
        set @sqlstmt = concat(
            'create ', unique_modifier, 'index ', given_index,
            ' on ', given_database, '.', given_table,
            ' (', given_columns, ')');

        prepare stmt from @sqlstmt;
        execute stmt;
        deallocate prepare stmt;
    else
        select concat(
            'index ',
            given_index,
            ' already exists on table ',
            given_database,
            '.',
            given_table);
    end if;
end $$
delimiter ;

SET SQL_NOTES=@OLD_SQL_NOTES;
