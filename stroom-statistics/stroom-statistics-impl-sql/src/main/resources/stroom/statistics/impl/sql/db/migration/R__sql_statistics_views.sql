-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- Some handy views for viewing the stats data, not used by the app

-- SQL_STAT_VAL_SRC
CREATE OR REPLACE VIEW v_stat_val_src AS
SELECT
	ssvs.NAME,
	FROM_UNIXTIME(ssvs.TIME_MS / 1000) TIME,
	ssvs.CT STAT_COUNT,
	ssvs.VAL STAT_VALUE_SUM,
    if(
        ssvs.VAL_TP = 2,
        ssvs.VAL / ssvs.CT,
        null) STAT_VALUE_AVG,
	if(
		ssvs.VAL_TP = 1,
		"COUNT",
		if(
			ssvs.VAL_TP = 2,
			"VALUE",
			null)) STAT_TYPE,
	if(
		ssvs.PROCESSING = 1,
		"YES",
		"NO") IS_PROCESSING_NOW
FROM SQL_STAT_VAL_SRC ssvs
ORDER BY
    ssvs.NAME,
    ssvs.TIME_MS;

-- SQL_STAT_VAL & SQL_STAT_KEY
CREATE OR REPLACE VIEW v_stat_val AS
SELECT
	ssk.NAME,
	if(
        ssv.VAL_TP = 1,
        "COUNT",
        if(
            ssv.VAL_TP = 2,
            "VALUE",
            null)) STAT_TYPE,
    FROM_UNIXTIME(ssv.TIME_MS / 1000) TIME,
    ssv.PRES STAT_PRECISION,
    ssv.CT STAT_COUNT,
    ssv.VAL STAT_VALUE_SUM,
    if(
        ssv.VAL_TP = 2,
        ssv.VAL / ssv.CT,
        null) STAT_VALUE_AVG
FROM SQL_STAT_KEY ssk
LEFT JOIN SQL_STAT_VAL ssv ON ssk.ID = ssv.FK_SQL_STAT_KEY_ID
ORDER BY
    ssk.NAME,
    ssv.TIME_MS;

SET SQL_NOTES=@OLD_SQL_NOTES;
