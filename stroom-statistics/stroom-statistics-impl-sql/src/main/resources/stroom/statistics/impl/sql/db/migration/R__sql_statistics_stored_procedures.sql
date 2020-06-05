-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DELIMITER //

DROP PROCEDURE IF EXISTS `stage1Upsert` //

CREATE PROCEDURE `stage1Upsert` (
    IN p_sqlPrecision tinyint(4),
    IN p_precision tinyint(4),
    IN p_valueType tinyint(4),
    IN p_aggregateToMs bigint(20),
    OUT p_rowCount int)
BEGIN
    DECLARE change_count INT DEFAULT 0;

    CREATE TEMPORARY TABLE TEMP_AGG AS (
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
            WHERE SSVS.TIME_MS < p_aggregateToMs
            AND SSVS.VAL_TP = p_valueType
            AND SSVS.PROCESSING = 1
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

    INSERT INTO SQL_STAT_VAL (
        FK_SQL_STAT_KEY_ID,
        TIME_MS,
        PRES,
        VAL_TP,
        VAL,
        CT)
    SELECT
        TEMP_AGG.FK_SQL_STAT_KEY_ID,
        TEMP_AGG.TIME_MS,
        TEMP_AGG.PRES,
        TEMP_AGG.VAL_TP,
        TEMP_AGG.VAL,
        TEMP_AGG.CT
    FROM TEMP_AGG
    WHERE TEMP_AGG.CT > 0
    ON DUPLICATE KEY UPDATE
       VAL = TEMP_AGG.VAL,
       CT = TEMP_AGG.CT;

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

    DROP TEMPORARY TABLE TEMP_AGG;
END //

DROP PROCEDURE IF EXISTS `stage2Upsert` //

CREATE PROCEDURE `stage2Upsert` (
    IN p_targetPrecision tinyint(4),
    IN p_targetSqlPrecision tinyint(4),
    IN p_lastPrecision tinyint(4),
    IN p_valueType tinyint(4),
    IN p_aggregateToMs bigint(20),
    OUT p_rowCount int)
BEGIN
    CREATE TEMPORARY TABLE TEMP_AGG AS (
--    CREATE TABLE TEMP_AGG AS (
        SELECT
            FK_SQL_STAT_KEY_ID,
            TIME_MS,
            SUM(COALESCE(VAL,0)) AS VAL,
            SUM(COALESCE(CT,0)) AS CT
        FROM (
            SELECT  -- existing values at correct precision
                SSVO.TIME_MS,
                SSVO.VAL,
                SSVO.CT,
                SSVO.FK_SQL_STAT_KEY_ID
            FROM SQL_STAT_VAL SSVO
            WHERE SSVO.PRES = p_targetPrecision  -- target pres
            AND SSVO.VAL_TP = p_valueType
            AND SSVO.TIME_MS <= ROUND(p_aggregateToMs, p_targetSqlPrecision)  -- only pick up records up to the point we are interested in
            UNION ALL
            SELECT
                ROUND(SSVN.TIME_MS, p_targetSqlPrecision),  -- target pres, e.g. -9
                SSVN.VAL,
                SSVN.CT,
                SSVN.FK_SQL_STAT_KEY_ID
            FROM SQL_STAT_VAL SSVN
            WHERE SSVN.TIME_MS < p_aggregateToMs
            AND SSVN.PRES = p_lastPrecision  -- old PRES
            AND SSVN.VAL_TP = p_valueType
        ) ROUNDED
        GROUP BY
            ROUNDED.FK_SQL_STAT_KEY_ID,
            ROUNDED.TIME_MS
    );

    INSERT INTO SQL_STAT_VAL (
        FK_SQL_STAT_KEY_ID,
        TIME_MS,
        PRES,
        VAL_TP,
        VAL,
        CT)
    SELECT
        TEMP_AGG.FK_SQL_STAT_KEY_ID,
        TEMP_AGG.TIME_MS,
        p_targetPrecision as PRES, -- target pres
        p_valueType as VAL_TP,
        TEMP_AGG.VAL AS VAL,
        TEMP_AGG.CT AS CT
    FROM TEMP_AGG
    ON DUPLICATE KEY UPDATE
        VAL = TEMP_AGG.VAL,
        CT = TEMP_AGG.CT;

    SET p_rowCount = ROW_COUNT();

    DROP TEMPORARY TABLE TEMP_AGG;
END //

DELIMITER ;

SET SQL_NOTES=@OLD_SQL_NOTES;
