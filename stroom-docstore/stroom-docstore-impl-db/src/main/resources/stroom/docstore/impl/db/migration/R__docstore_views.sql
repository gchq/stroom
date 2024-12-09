-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the doc store, NOT used by the app

-- Extract all the props common to all Docs then remove them from 'meta_dat' so only
-- the bespoke stuff is left
create or replace view v_doc as
select
    v.id,
    v.uuid,
    v.name,
    v.entry,
    json_value(v.meta_data, '$.type') doc_type,
    json_value(v.meta_data, '$.version') version,
    from_unixtime(json_value(v.meta_data, '$.createTimeMs') / 1000) create_time,
    json_value(v.meta_data, '$.createUser') create_user,
    from_unixtime(json_value(v.meta_data, '$.updateTimeMs') / 1000) update_time,
    json_value(v.meta_data, '$.updateUser') update_user,
    json_remove(
        v.meta_data,
        '$.uuid',
        '$.name',
        '$.type',
        '$.version',
        '$.createTimeMs',
        '$.createUser',
        '$.updateTimeMs',
        '$.updateUser') bespoke_meta_data, -- The json without all the common stuff in it
    v.content_data
from
    (select
        d.id,
        d.uuid,
        d.name,
        dc.entry entry,
        convert(dm.data using UTF8MB4) meta_data,
        convert(dc.data using UTF8MB4) content_data
    from doc d
    left join document_entry dm
        on (d.id = dm.fk_document_id and dm.entry = 'doc.meta')
    left join document_entry dc
        on (d.id = dc.fk_document_id and dc.entry != 'doc.meta')
    ) v;

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
