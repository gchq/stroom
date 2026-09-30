-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the doc store, NOT used by the app

-- Extract all the props common to all Docs then remove them from 'meta_dat' so only
-- the bespoke stuff is left
create or replace view v_doc as
select
    d.id,
    d.uuid,
    d.name,
    dd_content.ext content_extension,
    json_value(dd_meta.json_data, '$.type') doc_type,
    json_value(dd_meta.json_data, '$.version') version,
    from_unixtime(json_value(dd_meta.json_data, '$.createTimeMs') / 1000) create_time,
    json_value(dd_meta.json_data, '$.createUser') create_user,
    from_unixtime(json_value(dd_meta.json_data, '$.updateTimeMs') / 1000) update_time,
    json_value(dd_meta.json_data, '$.updateUser') update_user,
    json_remove(
        dd_meta.json_data,
        '$.uuid',
        '$.name',
        '$.type',
        '$.version',
        '$.createTimeMs',
        '$.createUser',
        '$.updateTimeMs',
        '$.updateUser') bespoke_meta_data, -- The json without all the common stuff in it
    coalesce(dd_content.json_data, dd_content.text_data, convert(dd_content.bin_data using UTF8MB4)) content_data
from doc d
join doc_data dd_meta
    on dd_meta.fk_doc_id = d.id
    and dd_meta.ext = 'meta'
left join doc_data dd_content
    on dd_content.fk_doc_id = d.id
    and dd_content.ext != 'meta'
where d.deleted is null;

-- A view for just feed docs, extracting all the json keys to columns
create or replace view v_feed_doc as
select
    vd.id,
    vd.uuid,
    vd.name,
    vd.version,
    vd.create_time,
    vd.create_user,
    vd.update_time,
    vd.update_user,
    json_value(vd.bespoke_meta_data, '$.description') description,
    json_value(vd.bespoke_meta_data, '$.classification') classification,
    json_value(vd.bespoke_meta_data, '$.encoding') encoding,
    json_value(vd.bespoke_meta_data, '$.contextEncoding') context_encoding,
    json_value(vd.bespoke_meta_data, '$.retentionDayAge') retention_day_age,
    json_value(vd.bespoke_meta_data, '$.reference') is_reference,
    json_value(vd.bespoke_meta_data, '$.streamType') streamType,
    json_value(vd.bespoke_meta_data, '$.status') status
from v_doc vd
where vd.doc_type = 'Feed';

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
