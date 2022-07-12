-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the meta data, NOT used by the app

create or replace view v_processor_task as
select
    pt.id task_id,
    pt.meta_id,
    pn.name node_name,
    pfd.name feed_name,
    pft.deleted filter_delted,
    pft.enabled filter_enabled,
    from_unixtime(pt.start_time_ms / 1000) task_start_time,
    from_unixtime(pt.end_time_ms / 1000) task_end_time,
    pft.id filter_id,
    pft.uuid filter_uuid,
    from_unixtime(pft.min_meta_create_time_ms / 1000) filter_min_meta_create_time,
    from_unixtime(pftr.min_meta_create_ms / 1000) tracker_min_meta_create_time,
    from_unixtime(pft.max_meta_create_time_ms / 1000) filter_max_meta_create_time,
    from_unixtime(pftr.max_meta_create_ms / 1000) tracker_max_meta_create_time,
    from_unixtime(pftr.meta_create_ms / 1000) tracker_meta_create_time,
    from_unixtime(pftr.last_poll_ms / 1000) tracker_last_poll_time,
    pftr.last_poll_task_count tracker_last_poll_task_count,
    pft.priority filter_priority,
    pft.reprocess filter_reprocess,
    p.deleted processor_deleted,
    p.enabled processor_enabled,
    p.pipeline_uuid,
    pftr.status tracker_status,
    pftr.event_count tracker_event_count,
    pftr.meta_count tracker_meta_count,
    pftr.min_event_id tracker_min_event_id,
    pftr.min_meta_id tracker_min_meta_id
from processor_task pt
inner join processor_filter pft on pt.fk_processor_filter_id = pft.id
inner join processor_node pn on pt.fk_processor_node_id = pn.id
inner join processor_feed pfd on pt.fk_processor_feed_id = pfd.id
inner join processor p on pft.fk_processor_id = p.id
inner join processor_filter_tracker pftr on pft.fk_processor_filter_tracker_id = pftr.id;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
