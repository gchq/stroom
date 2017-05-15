-- Create the SQL_STAT_ tables for the New SQL stats process.  These are duplicates of the legacy tables (STAT_KEY, STAT_VAL and STAT_VAL_SRC)

--
-- Table structure for table sql_stat_key
--
CREATE TABLE SQL_STAT_KEY (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table sql_stat_val
--
CREATE TABLE SQL_STAT_VAL (
  TIME_MS				bigint(20) NOT NULL,
  PRES					tinyint(4) NOT NULL,
  VAL_TP 				tinyint(4) NOT NULL,
  VAL					bigint(20) NOT NULL,
  CT					bigint(20) NOT NULL,
  FK_SQL_STAT_KEY_ID	bigint(20) NOT NULL,
  PRIMARY KEY (FK_SQL_STAT_KEY_ID, TIME_MS, VAL_TP, PRES),
  CONSTRAINT 			SQL_STAT_VAL_FK_STAT_KEY_ID FOREIGN KEY (FK_SQL_STAT_KEY_ID) REFERENCES SQL_STAT_KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- used for deletions of old data where the FK is not involved
CREATE INDEX SQL_STAT_VAL_TIME_MS ON SQL_STAT_VAL (TIME_MS);

--
-- Table structure for table sql_stat_val_src
--
CREATE TABLE SQL_STAT_VAL_SRC (
  TIME_MS			bigint(20) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  VAL_TP 			tinyint(4) NOT NULL,
  VAL				bigint(20) NOT NULL,
  PROCESSING        bit(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE INDEX SQL_STAT_VAL_SRC_PROCESSING_TIME_MS ON SQL_STAT_VAL_SRC (PROCESSING, TIME_MS);