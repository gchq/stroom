-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DELIMITER //

-- -----------------------------------------------------------------------

DROP PROCEDURE IF EXISTS `log_num_value` //

CREATE PROCEDURE `log_num_value` (
    INOUT p_log VARCHAR(1000),
    IN p_is_trace_enabled BOOLEAN,
    IN p_name VARCHAR(1000),
    IN p_value BIGINT)
BEGIN
    IF p_is_trace_enabled THEN
        SET p_log = CONCAT(p_log, '\n', p_name, ': ', COALESCE(p_value, -1));
    END IF;
END //

-- -----------------------------------------------------------------------

DROP PROCEDURE IF EXISTS `stage1Upsert` //

-- This sproc merges rows from SQL_STAT_VAL_SRC into SQL_STAT_VAL,
-- either by adding a new row or by merging the existing aggregate with
-- the aggregate of the grouped rows from SQL_STAT_VAL_SRC.
-- Must only be run one node and one thread.
CREATE PROCEDURE `stage1Upsert` (
    IN p_sqlPrecision tinyint,
    IN p_precision tinyint,
    IN p_valueType tinyint,
    IN p_aggregateToMs bigint,
    IN p_batch_max_id bigint,
    OUT p_rowCount int)
BEGIN
    DECLARE change_count INT DEFAULT 0;

    -- Build the new and merged aggregate counts/values in a temp table
    CREATE TEMPORARY TABLE TEMP_AGG_1 AS (
        SELECT
            SSVT.FK_SQL_STAT_KEY_ID as FK_SQL_STAT_KEY_ID,
            SSVT.TIME_MS_RND as TIME_MS,
            SSVT.PRES as PRES,
            SSVT.VAL_TP as VAL_TP,
            COALESCE(SSVT.VAL, 0) + COALESCE(SSV.VAL, 0) as VAL,
            COALESCE(SSVT.CT, 0) + COALESCE(SSV.CT, 0) as CT
        FROM (
            SELECT
                ROUND(SSVS.TIME_MS, p_sqlPrecision) AS TIME_MS_RND,
                p_precision as PRES,
                p_valueType as VAL_TP,
                SUM(COALESCE(SSVS.VAL,0)) as VAL,
                SUM(COALESCE(SSVS.CT,0)) as CT,
                SSK.ID as FK_SQL_STAT_KEY_ID
            FROM SQL_STAT_VAL_SRC SSVS
            JOIN SQL_STAT_KEY SSK ON (SSK.NAME = SSVS.NAME)
            WHERE SSVS.ID <= p_batch_max_id -- Use PK as a batching mechanism
            AND SSVS.TIME_MS < p_aggregateToMs -- Filter within our batch
            AND SSVS.VAL_TP = p_valueType -- Filter within our batch
            GROUP BY
                FK_SQL_STAT_KEY_ID,
                TIME_MS_RND,
                SSVS.VAL_TP,
                PRES
            HAVING COUNT(*) > 0
        ) SSVT
        LEFT JOIN SQL_STAT_VAL SSV ON (
           SSV.FK_SQL_STAT_KEY_ID = SSVT.FK_SQL_STAT_KEY_ID AND
           SSV.TIME_MS = SSVT.TIME_MS_RND AND
           SSV.PRES = SSVT.PRES AND
           SSV.VAL_TP = SSVT.VAL_TP
        )
    );

    -- Merge the temp table contents into SQL_STAT_VAL
    INSERT INTO SQL_STAT_VAL (
        FK_SQL_STAT_KEY_ID,
        TIME_MS,
        PRES,
        VAL_TP,
        VAL,
        CT)
    SELECT
        ta.FK_SQL_STAT_KEY_ID,
        ta.TIME_MS,
        ta.PRES,
        ta.VAL_TP,
        ta.VAL,
        ta.CT
    FROM TEMP_AGG_1 ta
    WHERE ta.CT > 0
    ON DUPLICATE KEY UPDATE
       VAL = VALUES(VAL),
       CT = VALUES(CT);

-- The following could be used if there are problems with the on dup key update
--    -- Update the records that are already in SSV
--    UPDATE SQL_STAT_VAL SSV
--    INNER JOIN TEMP_AGG TMP ON (
--       SSV.FK_SQL_STAT_KEY_ID = TMP.FK_SQL_STAT_KEY_ID AND
--       SSV.TIME_MS = TMP.TIME_MS AND
--       SSV.VAL_TP = TMP.VAL_TP AND
--       SSV.PRES = TMP.PRES)
--    SET
--        SSV.CT = TMP.CT,
--        SSV.VAL = TMP.VAL;
--
--    SET change_count = ROW_COUNT() + change_count;
--
--    -- Now add the records not in SSV
--    INSERT INTO SQL_STAT_VAL (
--        FK_SQL_STAT_KEY_ID,
--        TIME_MS,
--        PRES,
--        VAL_TP,
--        VAL,
--        CT)
--    SELECT
--        TMP.FK_SQL_STAT_KEY_ID,
--        TMP.TIME_MS,
--        TMP.PRES,
--        TMP.VAL_TP,
--        TMP.VAL,
--        TMP.CT
--    FROM TEMP_AGG TMP
--    WHERE TMP.CT > 0
--    AND NOT EXISTS (
--        SELECT NULL
--        FROM SQL_STAT_VAL SSV
--        WHERE TMP.FK_SQL_STAT_KEY_ID = SSV.FK_SQL_STAT_KEY_ID
--        AND TMP.TIME_MS = SSV.TIME_MS
--        AND TMP.VAL_TP = SSV.VAL_TP
--        AND TMP.PRES = SSV.PRES);

    SET change_count = ROW_COUNT() + change_count;
    SET p_rowCount = change_count;

    DROP TEMPORARY TABLE TEMP_AGG_1;
END //

-- -----------------------------------------------------------------------

DROP PROCEDURE IF EXISTS `stage2Upsert` //

-- Moves data at one precision to another (e.g. HOUR => DAY),
-- merging the re-computed fine grained stats into the existing course
-- grained stats (if there are any).
-- Must only be run one node and one thread.
CREATE PROCEDURE `stage2Upsert` (
    IN p_targetPrecision TINYINT,
    IN p_targetSqlPrecision TINYINT,
    IN p_lastPrecision TINYINT,
    IN p_valueType TINYINT,
    IN p_aggregateToMs BIGINT,
    IN p_batch_size INT,
    IN p_is_trace_enabled BOOLEAN,
    OUT p_upsertCount INT,
    OUT p_deleteCount INT,
    OUT p_log VARCHAR(1000))
BEGIN
    DECLARE v_batch_min_time_ms BIGINT;
    DECLARE v_batch_max_time_ms BIGINT;
    DECLARE v_rounded_batch_min_time_ms BIGINT;
    DECLARE v_rounded_batch_max_time_ms BIGINT;
    DECLARE v_old_data_count INT;
    DECLARE v_old_data_count_in_batch INT;
    DECLARE v_data_exists TINYINT;
    DECLARE v_log VARCHAR(1000);
    DECLARE v_val BIGINT;
    DECLARE v_ct BIGINT;

    SET v_log = '';
    SET p_upsertCount = 0;
    SET p_deleteCount = 0;
    SET v_val = 0;
    SET v_ct = 0;

    IF p_is_trace_enabled THEN
        CALL log_num_value(v_log, p_is_trace_enabled, 'p_valueType', p_valueType);
        CALL log_num_value(v_log, p_is_trace_enabled, 'p_targetPrecision', p_targetPrecision);
        CALL log_num_value(v_log, p_is_trace_enabled, 'p_lastPrecision', p_lastPrecision);
        CALL log_num_value(v_log, p_is_trace_enabled, 'p_batch_size', p_batch_size);
    END IF;

    -- Check if there are any rows that need moving to a new
    -- precision for this time range, precision
    -- and value type, returns zero if none exist, one
    -- otherwise.
    SELECT
       EXISTS (
           SELECT
               NULL
           FROM SQL_STAT_VAL SSV
           WHERE SSV.PRES = p_lastPrecision
           AND SSV.VAL_TP = p_valueType
           AND SSV.TIME_MS < p_aggregateToMs
       )
    INTO v_data_exists;

    IF v_data_exists = 1 THEN

        IF p_is_trace_enabled THEN
            CALL log_num_value(v_log, p_is_trace_enabled, 'v_data_exists', v_data_exists);

            SELECT COUNT(*)
            INTO v_old_data_count
            FROM SQL_STAT_VAL SSV
            WHERE SSV.PRES = p_lastPrecision  -- old PRES
            AND SSV.VAL_TP = p_valueType
            AND SSV.TIME_MS < p_aggregateToMs;

            CALL log_num_value(v_log, p_is_trace_enabled, 'v_old_data_count', v_old_data_count);
        END IF;

        -- We have at least one record that needs moving to a coarser time bucket so
        -- process the row up to p_aggregateToMs in batches.
        -- This means we can run agg stage 2 in smaller batches to make stopping it quicker.
        -- We grab the latest time of the oldest N rows that are below the aggregation threshold
        -- and then work off that time in the rest of the sql.
        SELECT
            MIN(TIME_MS),
            MAX(TIME_MS)
        INTO
            v_batch_min_time_ms,
            v_batch_max_time_ms
        FROM (
            SELECT
                ssv.TIME_MS
            FROM SQL_STAT_VAL ssv
            WHERE ssv.PRES = p_lastPrecision
            AND ssv.VAL_TP = p_valueType
            AND ssv.TIME_MS < p_aggregateToMs
            ORDER BY ssv.TIME_MS
            LIMIT p_batch_size) v;

        SET v_rounded_batch_min_time_ms = ROUND(v_batch_min_time_ms, p_targetSqlPrecision);
        SET v_rounded_batch_max_time_ms = ROUND(v_batch_max_time_ms, p_targetSqlPrecision);

        IF p_is_trace_enabled THEN
            CALL log_num_value(v_log, p_is_trace_enabled, 'v_batch_min_time_ms', v_batch_min_time_ms);
            CALL log_num_value(v_log, p_is_trace_enabled, 'v_batch_max_time_ms', v_batch_max_time_ms);
            CALL log_num_value(v_log, p_is_trace_enabled, 'v_rounded_batch_min_time_ms', v_rounded_batch_min_time_ms);
            CALL log_num_value(v_log, p_is_trace_enabled, 'v_rounded_batch_max_time_ms', v_rounded_batch_max_time_ms);

            SELECT COUNT(*)
            INTO v_old_data_count_in_batch
            FROM SQL_STAT_VAL SSV
            WHERE SSV.PRES = p_lastPrecision  -- old PRES
            AND SSV.VAL_TP = p_valueType
            AND SSV.TIME_MS <= v_batch_max_time_ms;

            CALL log_num_value(v_log, p_is_trace_enabled, 'v_old_data_count_in_batch', v_old_data_count_in_batch);
        END IF;

        -- Combine the aggregates from the existing data in the target precision with the data that
        -- needs to be moved to the target precision.
        -- Inserted into a temp table as we had issue with the ON DUPLICATE KEY
        CREATE TEMPORARY TABLE TEMP_AGG_2 AS (
            SELECT
                FK_SQL_STAT_KEY_ID,
                TIME_MS,
                SUM(COALESCE(VAL,0)) AS VAL,
                SUM(COALESCE(CT,0)) AS CT
            FROM (
                (   -- existing values at correct precision
                    SELECT
                        SSVO.TIME_MS,
                        SSVO.VAL,
                        SSVO.CT,
                        SSVO.FK_SQL_STAT_KEY_ID
                    FROM SQL_STAT_VAL SSVO
                    WHERE SSVO.PRES = p_targetPrecision  -- target pres
                    AND SSVO.VAL_TP = p_valueType
                    AND SSVO.TIME_MS <= v_rounded_batch_max_time_ms  -- only pick up records up to the point we are interested in
                    AND SSVO.TIME_MS >= v_rounded_batch_max_time_ms  -- only pick up records up to the point we are interested in
                )
                UNION ALL
                (   -- values at a finer precision that need to be aggregated into the target precision
                    -- The times get rounded to the same precision as the ones selected in the other half
                    -- of the union.
                    -- We don't need to use v_batch_min_time_ms as that data below that point will have been
                    -- deleted in a previous batch.
                    SELECT
                        ROUND(SSVN.TIME_MS, p_targetSqlPrecision),  -- pres, e.g. -9
                        SSVN.VAL,
                        SSVN.CT,
                        SSVN.FK_SQL_STAT_KEY_ID
                    FROM SQL_STAT_VAL SSVN
                    WHERE SSVN.PRES = p_lastPrecision  -- old PRES
                    AND SSVN.VAL_TP = p_valueType
                    AND SSVN.TIME_MS <= v_batch_max_time_ms -- Only work on data up to our batch limit
                )
            ) ROUNDED
            GROUP BY
                ROUNDED.FK_SQL_STAT_KEY_ID,
                ROUNDED.TIME_MS
        );

        -- Merge the (re-)?computed stats into SQL_STAT_VAL either replacing what is
        -- there or inserting new
        INSERT INTO SQL_STAT_VAL (
            FK_SQL_STAT_KEY_ID,
            TIME_MS,
            PRES,
            VAL_TP,
            VAL,
            CT)
        SELECT
            ta.FK_SQL_STAT_KEY_ID,
            ta.TIME_MS,
            p_targetPrecision as PRES, -- target pres
            p_valueType as VAL_TP,
            ta.VAL AS VAL,
            ta.CT AS CT
        FROM TEMP_AGG_2 ta
        ON DUPLICATE KEY UPDATE
            VAL = VALUES(VAL),
            CT = VALUES(CT);

        SET p_upsertCount = ROW_COUNT();
        CALL log_num_value(v_log, p_is_trace_enabled, 'p_upsertCount', p_upsertCount);

        DROP TEMPORARY TABLE TEMP_AGG_2;

        IF p_upsertCount > 0 THEN
            -- Now delete all the records that we have aggregated up to a courser level
            DELETE FROM SQL_STAT_VAL
            WHERE PRES = p_lastPrecision
            AND VAL_TP = p_valueType
            AND TIME_MS <= v_batch_max_time_ms;

            SET p_deleteCount = ROW_COUNT();
            CALL log_num_value(v_log, p_is_trace_enabled, 'p_deleteCount', p_deleteCount);
        ELSE
            SET p_deleteCount = 0;
        END IF;
    END IF;

    SET p_log = v_log;
END //

DELIMITER ;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set tabstop=4 shiftwidth=4 expandtab:
