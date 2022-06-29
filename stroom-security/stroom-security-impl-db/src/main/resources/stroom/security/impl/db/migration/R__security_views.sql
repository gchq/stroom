-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing security tables, NOT used by the app

-- View to show all the app/doc permissions for a user/group including ones
-- inherited from group memberships
create or replace view v_permission as
select
    su.uuid,
    su.name,
    su.is_group,
    null via_group,
    null doc_name,
    null doc_type,
    ap.permission,
    'App' permission_type
from app_permission ap
inner join stroom_user su on su.uuid = ap.user_uuid
union
select
    su_user.uuid,
    su_user.name,
    su_user.is_group,
    su_grp.name via_group,
    null doc_name,
    null doc_type,
    ap.permission,
    'App' permission_type
from app_permission ap
inner join stroom_user su_grp on su_grp.uuid = ap.user_uuid
inner join stroom_user_group sug on su_grp.uuid = sug.group_uuid
inner join stroom_user su_user on su_user.uuid = sug.user_uuid
union
select
    su.uuid,
    su.name,
    su.is_group,
    null via_group,
    d.name doc_name,
    d.type doc_type,
    dp.permission,
    'Document' permission_type
from doc_permission dp
inner join stroom_user su on su.uuid = dp.user_uuid
inner join doc d on d.uuid = dp.doc_uuid
where d.ext = 'meta'
union
select
    su_user.uuid,
    su_user.name,
    su_user.is_group,
    su_grp.name via_group,
    d.name doc_name,
    d.type doc_type,
    dp.permission,
    'Document' permission_type
from doc_permission dp
inner join stroom_user su_grp on su_grp.uuid = dp.user_uuid
inner join stroom_user_group sug on su_grp.uuid = sug.group_uuid
inner join stroom_user su_user on su_user.uuid = sug.user_uuid
inner join doc d on d.uuid = dp.doc_uuid
where d.ext = 'meta';

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
