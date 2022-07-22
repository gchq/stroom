-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the doc store, NOT used by the app

create or replace view v_fs_volume as
select
    fv.id fs_volume_id,
    fv.path,
    case fv.status -- FsVolume.VolumeUseState
        when 0 then 'Active'
        when 1 then 'Inactive'
        when 3 then 'Closed'
        else concat('ERROR: UNKNOWN STATE (', fv.status, ')')
        end status,
    fv.byte_limit,
    from_unixtime(fv.create_time_ms / 1000) create_time,
    fv.update_user create_user,
    from_unixtime(fv.update_time_ms / 1000) update_time,
    fv.update_user update_user,
    fvs.id,
    fvs.bytes_used,
    fvs.bytes_free,
    fvs.bytes_total
from fs_volume fv
inner join fs_volume_state fvs on fv.fk_fs_volume_state_id = fvs.id;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
