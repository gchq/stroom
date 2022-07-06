-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing security tables, NOT used by the app

-- View to show all the app/doc permissions for a user/group including ones
-- inherited from group memberships
-- Can't join to doc to get the name as the table is in another module and therefore
-- potentially in a different db to this view.
create or replace view v_permission as
select -- Direct app perms
    su.uuid user_or_group_uuid,
    su.name user_or_group_name,
    su.is_group,
    null via_group,
    null doc_uuid,
    ap.permission,
    'App' permission_type
from app_permission ap
inner join stroom_user su on su.uuid = ap.user_uuid
union
select -- App perms via group
    su_user.uuid user_or_group_uuid,
    su_user.name user_or_group_name,
    su_user.is_group,
    su_grp.name via_group,
    null doc_uuid,
    ap.permission,
    'App' permission_type
from app_permission ap
inner join stroom_user su_grp on su_grp.uuid = ap.user_uuid
inner join stroom_user_group sug on su_grp.uuid = sug.group_uuid
inner join stroom_user su_user on su_user.uuid = sug.user_uuid
union
select -- Direct doc perms
    su.uuid user_or_group_uuid,
    su.name user_or_group_name,
    su.is_group,
    null via_group,
    dp.doc_uuid,
    dp.permission,
    'Document' permission_type
from doc_permission dp
inner join stroom_user su on su.uuid = dp.user_uuid
union
select -- Doc perms via group
    su_user.uuid user_or_group_uuid,
    su_user.name user_or_group_name,
    su_user.is_group,
    su_grp.name via_group,
    dp.doc_uuid,
    dp.permission,
    'Document' permission_type
from doc_permission dp
inner join stroom_user su_grp on su_grp.uuid = dp.user_uuid
inner join stroom_user_group sug on su_grp.uuid = sug.group_uuid
inner join stroom_user su_user on su_user.uuid = sug.user_uuid;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
