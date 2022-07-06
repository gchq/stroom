-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the doc store, NOT used by the app

create or replace view v_index_volume as
select
    iv.id index_volume_id,
    from_unixtime(iv.create_time_ms / 1000) create_time,
    iv.create_user,
    from_unixtime(iv.update_time_ms / 1000) update_time,
    iv.update_user,
    iv.node_name,
    iv.path,
    ivg.id index_volume_group_id,
    ivg.name index_volume_group_name,
    case iv.state
        when 0 then 'Active'
        when 1 then 'Inactive'
        when 3 then 'Closed'
        else concat('UNKNOWN STATE (', iv.state, ')')
        end index_volume_state,
    iv.bytes_limit,
    iv.bytes_used,
    iv.bytes_free,
    iv.bytes_total,
    from_unixtime(iv.status_ms / 1000) status_time
from index_volume iv
inner join index_volume_group ivg on iv.fk_index_volume_group_id = ivg.id;

create or replace view v_index_shard as
select
    s.id index_shard_id,
    s.node_name,
    iv.index_volume_id,
    iv.path volume_path,
    iv.node_name volume_node_name,
    iv.index_volume_group_id,
    iv.index_volume_group_name,
    s.old_index_id,
    s.index_uuid,
    s.commit_document_count,
    s.commit_duration_ms,
    from_unixtime(s.commit_ms / 1000) last_commit_time,
    s.document_count,
    s.file_size,
    case s.status -- IndexShardStatus
        when 0 then 'Closed'
        when 1 then 'Open'
        when 10 then 'Closing'
        when 20 then 'Opening'
        when 30 then 'New'
        when 99 then 'Deleted'
        when 100 then 'Corrupt'
        else concat('UNKNOWN STATE (', s.status, ')')
        end index_shard_status,
    s.index_version,
    s.partition_name,
    from_unixtime(s.partition_from_ms / 1000) partition_from_time,
    from_unixtime(s.partition_to_ms / 1000) partition_to_time
from index_shard s
inner join v_index_volume iv on s.fk_volume_id = iv.index_volume_id;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
