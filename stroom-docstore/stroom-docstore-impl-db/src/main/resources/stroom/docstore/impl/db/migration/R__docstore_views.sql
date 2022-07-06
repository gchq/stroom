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
    v.content_extension,
    json_extract(v.meta_data, '$.type') doc_type,
    json_extract(v.meta_data, '$.version') version,
    from_unixtime(json_extract(v.meta_data, '$.createTimeMs') / 1000) createTime,
    json_extract(v.meta_data, '$.createUser') createUser,
    from_unixtime(json_extract(v.meta_data, '$.updateTimeMs') / 1000) updateTime,
    json_extract(v.meta_data, '$.updateUser') updateUser,
    json_remove(
        v.meta_data,
        '$.uuid',
        '$.name',
        '$.type',
        '$.version',
        '$.createTimeMs',
        '$.createUser',
        '$.updateTimeMs',
        '$.updateUser') bespoke_meta_data,
    v.content_data
from
    (select
        dm.id,
        dm.uuid,
        dm.name,
        dd.ext content_extension,
        convert(dm.data using UTF8MB4) meta_data,
        convert(dd.data using UTF8MB4) content_data
    from doc dm
    left join doc dd
        on dm.uuid = dd.uuid
        and dd.ext != dm.ext
    where dm.ext = 'meta') v;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
