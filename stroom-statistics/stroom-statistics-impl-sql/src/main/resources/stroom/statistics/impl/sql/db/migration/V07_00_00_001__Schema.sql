-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- NOTE This is pretty much a copy of V4_0_60 in stroom v6, but as schema_version has
-- been renamed to statistics_schema_history, flyway will know nothing of the previous
-- schema. Thus we can start afresh with everything written to copy with the object already
-- existing. Renamed to V7_00_00_001 to avoid the confusion of a V4 script running in
-- a v6 ==> v7 migration.


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

--
-- Table structure for table sql_stat_val
--
CREATE TABLE IF NOT EXISTS SQL_STAT_VAL (
  TIME_MS				bigint(20) NOT NULL,
  PRES					tinyint(4) NOT NULL,
  VAL_TP 				tinyint(4) NOT NULL,
  VAL					bigint(20) NOT NULL,
  CT					bigint(20) NOT NULL,
  FK_SQL_STAT_KEY_ID	bigint(20) NOT NULL,
  PRIMARY KEY (FK_SQL_STAT_KEY_ID, TIME_MS, VAL_TP, PRES),
  CONSTRAINT 			SQL_STAT_VAL_FK_STAT_KEY_ID
      FOREIGN KEY (FK_SQL_STAT_KEY_ID)
      REFERENCES SQL_STAT_KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CALL statistics_create_index_v1(
    'SQL_STAT_VAL',
    'SQL_STAT_VAL_TIME_MS',
    false,
    'TIME_MS');

--
-- Table structure for table sql_stat_val_src
--
CREATE TABLE IF NOT EXISTS SQL_STAT_VAL_SRC (
  TIME_MS			bigint(20) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  VAL_TP 			tinyint(4) NOT NULL,
  VAL				bigint(20) NOT NULL,
  PROCESSING        bit(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CALL statistics_create_index_v1(
    'SQL_STAT_VAL_SRC',
    'SQL_STAT_VAL_SRC_PROCESSING_TIME_MS',
    false,
    'PROCESSING, TIME_MS');

SET SQL_NOTES=@OLD_SQL_NOTES;
