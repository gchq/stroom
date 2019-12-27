-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- NOTE This is pretty much a copy of V4_0_60 in stroom v6, but as schema_version has
-- been renamed to statistics_schema_history, flyway will know nothing of the previous
-- schema. Thus we can start afresh with everything written to copy with the object already
-- existing. Renamed to V7_00_00_001 to avoid the confusion of a V4 script running in
-- a v6 =>> v7 migration.


-- Create the SQL_STAT_ tables for the New SQL stats process.  These are duplicates of the legacy tables (STAT_KEY, STAT_VAL and STAT_VAL_SRC)

--
-- Table structure for table sql_stat_key
--
CREATE TABLE IF NOT EXISTS SQL_STAT_KEY (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
SET SQL_NOTES=@SQL_NOTES;

--
-- Table structure for table sql_stat_val
--
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;
CREATE TABLE IF NOT EXISTS SQL_STAT_VAL (
  TIME_MS				bigint(20) NOT NULL,
  PRES					tinyint(4) NOT NULL,
  VAL_TP 				tinyint(4) NOT NULL,
  VAL					bigint(20) NOT NULL,
  CT					bigint(20) NOT NULL,
  FK_SQL_STAT_KEY_ID	bigint(20) NOT NULL,
  PRIMARY KEY (FK_SQL_STAT_KEY_ID, TIME_MS, VAL_TP, PRES),
  CONSTRAINT 			SQL_STAT_VAL_FK_STAT_KEY_ID FOREIGN KEY (FK_SQL_STAT_KEY_ID) REFERENCES SQL_STAT_KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
SET SQL_NOTES=@SQL_NOTES;

--
-- Create an index if it doesn't exist
--
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;
DROP PROCEDURE IF EXISTS createIndex;
SET SQL_NOTES=@SQL_NOTES;
DELIMITER //
CREATE PROCEDURE createIndex ()
BEGIN
  IF (SELECT NOT EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_NAME = 'SQL_STAT_VAL' AND INDEX_NAME = 'SQL_STAT_VAL_TIME_MS')) THEN
    CREATE INDEX SQL_STAT_VAL_TIME_MS ON SQL_STAT_VAL (TIME_MS);
  END IF;
END//
DELIMITER ;
CALL createIndex();
DROP PROCEDURE createIndex;

--CREATE INDEX SQL_STAT_VAL_TIME_MS O?N SQL_STAT_VAL (TIME_MS);

--
-- Table structure for table sql_stat_val_src
--
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;
CREATE TABLE IF NOT EXISTS SQL_STAT_VAL_SRC (
  TIME_MS			bigint(20) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  VAL_TP 			tinyint(4) NOT NULL,
  VAL				bigint(20) NOT NULL,
  PROCESSING        bit(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
SET SQL_NOTES=@SQL_NOTES;

--
-- Create an index if it doesn't exist
--
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;
DROP PROCEDURE IF EXISTS createIndex;
SET SQL_NOTES=@SQL_NOTES;
DELIMITER //
CREATE PROCEDURE createIndex ()
BEGIN
  IF (SELECT NOT EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_NAME = 'SQL_STAT_VAL_SRC' AND INDEX_NAME = 'SQL_STAT_VAL_SRC_PROCESSING_TIME_MS')) THEN
    CREATE INDEX SQL_STAT_VAL_SRC_PROCESSING_TIME_MS ON SQL_STAT_VAL_SRC (PROCESSING, TIME_MS);
  END IF;
END//
DELIMITER ;
CALL createIndex();
DROP PROCEDURE createIndex;

--CREATE INDEX SQL_STAT_VAL_SRC_PROCESSING_TIME_MS ON SQL_STAT_VAL_SRC (PROCESSING, TIME_MS);

SET SQL_NOTES=@SQL_NOTES;
