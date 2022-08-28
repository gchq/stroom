-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the doc store, NOT used by the app

create or replace view v_job_node as
select
    jn.id job_node_id,
    j.id job_id,
    j.name job_name,
    jn.node_name,
    j.enabled job_enabled,
    jn.enabled job_node_enabled,
    case jn.job_type -- JobNode.JobType
        when 0 then 'UNKNOWN'
        when 1 then 'Cron'
        when 2 then 'Frequency'
        when 3 then 'Distributed'
        else concat('ERROR: UNKNOWN STATE (', jn.job_type, ')')
        end job_type,
    jn.schedule,
    jn.task_limit,
    from_unixtime(j.update_time_ms / 1000) job_update_time,
    j.update_user job_update_user,
    from_unixtime(jn.update_time_ms / 1000) job_node_update_time,
    jn.update_user job_node_update_user
from job j
inner join job_node jn on jn.job_id = j.id;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
