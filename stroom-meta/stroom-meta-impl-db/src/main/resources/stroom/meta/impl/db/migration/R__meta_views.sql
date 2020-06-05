-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for admins viewing the meta data, NOT used by the app

-- meta, meta_feed, meta_type, meta_processor
CREATE OR REPLACE VIEW v_meta AS
    SELECT
        FROM_UNIXTIME(m.create_time / 1000) create_time,
        FROM_UNIXTIME(m.effective_time / 1000) effective_time,
        m.parent_id,
        m.status,
        CASE m.status
            WHEN 0 THEN "UNLOCKED"
            WHEN 1 THEN "LOCKED"
            WHEN 99 THEN "DELETED"
            ELSE "?"
            END status_str,
        FROM_UNIXTIME(m.status_time / 1000) status_time,
        mf.name feed_name,
        mt.name type,
        mp.processor_uuid,
        mp.pipeline_uuid
    FROM meta m
    LEFT JOIN meta_feed mf ON m.feed_id = mf.id
    LEFT JOIN meta_type mt ON m.type_id = mt.id
    LEFT JOIN meta_processor mp ON m.processor_id = mp.id;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
