

-- NOTE: A MySQL index can only hold 767 bytes per key (irrespective of the number of columns in the key)
-- For latin1 this means a maximum of 767 chars
-- For utf8 this means a maximum of 255 chars (3 bytes per char)

-- NAMING CONVENTIONS !
-- UNIQUE KEY 		- <COLUMN_NAMES_IN_KEY>_IDX
-- KEY 				- <COLUMN_NAMES_IN_KEY>_IDX
-- CONSTRAINT 		- <SOURCE_TABLE>_<COLUMN_NAMES_IN_CONSTRAINT>

--
-- Table structure for table sys_grp
--
CREATE TABLE SYS_GRP (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER  		varchar(255) default NULL,
  UPD_DT  			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  GLOB 				bit(1) NOT NULL,
  COMP_AUTH 		tinyint(4) NOT NULL,
  FK_SYS_GRP_ID  	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID, NAME),
  CONSTRAINT  		SYS_GRP_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table sys_role
--
CREATE TABLE SYS_ROLE (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER  		varchar(255) default NULL,
  UPD_DT  			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table sys_perm
--
CREATE TABLE SYS_PERM (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER  		varchar(255) default NULL,
  UPD_DT  			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  PERM_TP			varchar(255) NOT NULL,
  UNIQUE 			(NAME, PERM_TP)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Table structure for table sys_usr
--
CREATE TABLE SYS_USR (
  ID				int auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT			timestamp NULL DEFAULT NULL,
  UPD_USER			varchar(255) default NULL,
  STAT				tinyint(4) NOT NULL,
  CUR_LOGIN_FAIL	smallint(6) NOT NULL,
  HUMAN				bit(1) NOT NULL,
  LOGIN_EXPIRY		bit(1) NOT NULL,
  LAST_LOGIN_MS		bigint(20) default NULL,
  LOGIN_VALID_MS	bigint(20) default NULL,
  NAME				varchar(255) NOT NULL,
  PASS_EXPIRY_MS	bigint(20) default NULL,
  PASS_HASH			varchar(255) default NULL,
  PASS_SALT			varchar(255) default NULL,
  TOTL_LOGIN_FAIL	smallint(6) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table sys_usr_grp
--
CREATE TABLE SYS_USR_GRP (
  FK_SYS_USR_ID 	int(11) NOT NULL,
  FK_SYS_GRP_ID		int(11) NOT NULL,
  PRIMARY KEY 		(FK_SYS_USR_ID,FK_SYS_GRP_ID),
  CONSTRAINT 		SYS_USR_GRP_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID),
  CONSTRAINT 		SYS_USR_GRP_FK_SYS_USR_ID FOREIGN KEY (FK_SYS_USR_ID) REFERENCES SYS_USR (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table sys_usr_role
--
CREATE TABLE SYS_USR_ROLE (
  FK_SYS_USR_ID 	int(11) NOT NULL,
  FK_SYS_ROLE_ID 	int(11) NOT NULL,
  PRIMARY KEY  		(FK_SYS_USR_ID, FK_SYS_ROLE_ID),
  CONSTRAINT 		SYS_USR_ROLE_FK_SYS_USR_ID FOREIGN KEY (FK_SYS_USR_ID) REFERENCES SYS_USR (ID),
  CONSTRAINT 		SYS_USR_ROLE_FK_SYS_ROLE_ID FOREIGN KEY (FK_SYS_ROLE_ID) REFERENCES SYS_ROLE (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table sys_role_perm
--
CREATE TABLE SYS_ROLE_PERM (
  FK_SYS_ROLE_ID 	int(11) NOT NULL,
  FK_SYS_PERM_ID 	int(11) NOT NULL,
  PRIMARY KEY  		(FK_SYS_ROLE_ID, FK_SYS_PERM_ID),
  CONSTRAINT 		SYS_ROLE_PERM_FK_SYS_ROLE_ID FOREIGN KEY (FK_SYS_ROLE_ID) REFERENCES SYS_ROLE (ID),
  CONSTRAINT 		SYS_ROLE_PERM_FK_SYS_PERM_ID FOREIGN KEY (FK_SYS_PERM_ID) REFERENCES SYS_PERM (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table rack
--
CREATE TABLE RK (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME				varchar(255) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table node
--
CREATE TABLE ND (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  CLSTR_URL			varchar(255) default NULL,
  NAME				varchar(255) NOT NULL,
  PRIOR				smallint(6) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  FK_RK_ID 			int(11) NOT NULL,
  UNIQUE			(NAME),
  CONSTRAINT 		ND_FK_RK_ID FOREIGN KEY (FK_RK_ID) REFERENCES RK (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table vol_state
--
CREATE TABLE VOL_STATE (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  BYTES_USED		bigint(20) default NULL,
  BYTES_FREE		bigint(20) default NULL,
  BYTES_TOTL		bigint(20) default NULL,
  STAT_MS 			bigint(20) default NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table vol
--
CREATE TABLE VOL (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  PATH 				varchar(255) NOT NULL,
  IDX_STAT 			tinyint(4) NOT NULL,
  STRM_STAT 		tinyint(4) NOT NULL,
  VOL_TP 			tinyint(4) NOT NULL,
  BYTES_LMT			bigint(20) default NULL,
  FK_ND_ID 			int(11) NOT NULL,
  FK_VOL_STATE_ID 	int(11) NOT NULL,
  UNIQUE 			(FK_ND_ID, PATH),
  CONSTRAINT 		VOL_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID),
  CONSTRAINT 		VOL_STAT_FK_VOL_STATE_ID FOREIGN KEY (FK_VOL_STATE_ID) REFERENCES VOL_STATE (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table bmark
--
CREATE TABLE BMARK (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  NAME				varchar(255) NOT NULL,
  STAT_MS 			bigint(20) NOT NULL,
  VAL				int(11) NOT NULL,
  FK_ND_ID 			int(11) NOT NULL,
  CONSTRAINT BMARK_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table clstr_lk
--
CREATE TABLE CLSTR_LK (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  NAME 				varchar(255) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table dict
--
CREATE TABLE DICT (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID, NAME),
  CONSTRAINT 		DICT_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table strm_type
--
CREATE TABLE STRM_TP (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  EXT 				varchar(255) NOT NULL,
  PURP 				tinyint(4) NOT NULL,
  PATH 				varchar(255) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table feed
--
CREATE TABLE FD (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  STAT 				tinyint(4) NOT NULL,
  CLS 				varchar(255) default NULL,
  CTX_ENC 			varchar(255) default NULL,
  ENC 				varchar(255) default NULL,
  REF				bit(1) NOT NULL,
  RETEN_DAY_AGE 	int(11) default NULL,
  FK_SYS_GRP_ID 	int(11) default NULL,
  FK_STRM_TP_ID 	int(11) NOT NULL,
  UNIQUE  			(NAME),
  CONSTRAINT 		FD_FK_STRM_TP_ID FOREIGN KEY (FK_STRM_TP_ID) REFERENCES STRM_TP (ID),
  CONSTRAINT 		FD_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table glob_prop
--
CREATE TABLE GLOB_PROP (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  VAL 				longtext NOT NULL,
  UNIQUE  			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table idx
--
CREATE TABLE IDX (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  MAX_DOC			int(11) NOT NULL,
  MAX_SHRD			int(11) NOT NULL,
  PART_BY			tinyint(4) default NULL,
  PART_SZ			int(11) NOT NULL,
  RETEN_DAY_AGE 	int(11) default NULL,
  FLDS 				longtext,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID, NAME),
  CONSTRAINT 		IDX_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table idx_vol
--
CREATE TABLE IDX_VOL (
  FK_IDX_ID 	int(11) NOT NULL,
  FK_VOL_ID 	int(11) NOT NULL,
  PRIMARY KEY  		(FK_IDX_ID, FK_VOL_ID),
  CONSTRAINT 		IDX_FK_VOL_ID FOREIGN KEY (FK_VOL_ID) REFERENCES VOL (ID),
  CONSTRAINT 		VOL_FK_IDX_ID FOREIGN KEY (FK_IDX_ID) REFERENCES IDX (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table idx_shrd
--
CREATE TABLE IDX_SHRD (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  CMT_DOC_CT 		int(11) default NULL,
  CMT_DUR_MS 		bigint(20) default NULL,
  CMT_MS 			bigint(20) default NULL,
  DOC_CT 			int(11) NOT NULL,
  FILE_SZ 			bigint(20) default NULL,
  STAT 				tinyint(4) NOT NULL,
  FK_IDX_ID	 		int(11) NOT NULL,
  FK_ND_ID 			int(11) NOT NULL,
  PART				varchar(255) NOT NULL,
  PART_FROM_DT 		timestamp NULL DEFAULT NULL,
  PART_TO_DT 		timestamp NULL DEFAULT NULL,
  FK_VOL_ID 		int(11) NOT NULL,
  IDX_VER			varchar(255) default NULL,
  CONSTRAINT 		IDX_SHRD_FK_VOL_ID FOREIGN KEY (FK_VOL_ID) REFERENCES VOL (ID),
  CONSTRAINT 		IDX_SHRD_FK_IDX_ID FOREIGN KEY (FK_IDX_ID) REFERENCES IDX (ID),
  CONSTRAINT 		IDX_SHRD_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table job
--
CREATE TABLE JB (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table job_node
--
CREATE TABLE JB_ND (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  JB_TP 			tinyint(4) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  SCHEDULE 			varchar(255) default NULL,
  TASK_LMT 			int(11) NOT NULL,
  FK_JB_ID 			int(11) NOT NULL,
  FK_ND_ID 			int(11) NOT NULL,
  UNIQUE 			(FK_ND_ID,FK_JB_ID),
  CONSTRAINT 		JB_ND_FK_JB_ID FOREIGN KEY (FK_JB_ID) REFERENCES JB (ID),
  CONSTRAINT 		JB_ND_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;



--
-- Table structure for table pipe
--
CREATE TABLE PIPE (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  DAT 				longtext,
  PIPE_TP 			varchar(255) default NULL,
  MIG_FEED_ID		int(11) default NULL,
  MIG_TRAN_ID		int(11) default NULL,
  FK_SYS_GRP_ID 	int(11) default NULL,
  FK_PIPE_ID 		int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT 		PIPE_FK_PIPE_ID FOREIGN KEY (FK_PIPE_ID) REFERENCES PIPE (ID),
  CONSTRAINT 		PIPE_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;





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


--
-- Table structure for table search
--
CREATE TABLE SRCH (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT 		SRCH_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


-- The following three STAT_ tables are legacy tables for the legacy stats process.  They are superseeded by the NEW_STAT_ tables
-- that are used by the new SQL stats process.  The legacy tables are due for removal sometime following the transition to Stroom 4.0.
-- They are only used for injest of stats via the legacy 'statisticFilter' pipeline element and there is no mechanism in Stroom to
-- query them.  Querying them is only via MySQL directly.

--
-- Table structure for table stat_key
--
CREATE TABLE STAT_KEY (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table stat_val
--

CREATE TABLE STAT_VAL (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_MS			bigint(20) NOT NULL,
  PRES				tinyint(4) NOT NULL,
  VAL_TP 			tinyint(4) NOT NULL,
  VAL				bigint(20) NOT NULL,
  CT				bigint(20) NOT NULL,
  FK_STAT_KEY_ID	bigint(20) NOT NULL,
  CONSTRAINT 		STAT_VAL_FK_STAT_KEY_ID FOREIGN KEY (FK_STAT_KEY_ID) REFERENCES STAT_KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- enforce data integrity as we should never have multiple rows for these four columns
CREATE UNIQUE INDEX STAT_VAL_FK_STAT_KEY_ID_CRT_MS_VAL_TP_PRES ON STAT_VAL (FK_STAT_KEY_ID, CRT_MS, VAL_TP, PRES);


--
-- Table structure for table stat_val_src
--
CREATE TABLE STAT_VAL_SRC (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_MS			bigint(20) NOT NULL,
  NAME 				varchar(766) NOT NULL,
  VAL_TP 			tinyint(4) NOT NULL,
  VAL				bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table strm_proc
--
CREATE TABLE STRM_PROC (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  TASK_TP 			varchar(255) default NULL,
  FK_PIPE_ID 		int(11) default NULL,
  ENBL				bit(1) NOT NULL,
  CONSTRAINT STRM_PROC_FK_PIPE_ID FOREIGN KEY (FK_PIPE_ID) REFERENCES PIPE (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table strm
--
CREATE TABLE STRM (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) NOT NULL,
  EFFECT_MS 		bigint(20) default NULL,
  PARNT_STRM_ID 	bigint(20) default NULL,
  STAT 				tinyint(4) NOT NULL,
  STAT_MS           bigint(20) default NULL,
  STRM_TASK_ID      bigint(20) default NULL,
  FK_FD_ID 			int(11) NOT NULL,
  FK_STRM_PROC_ID 	int(11) default NULL,
  FK_STRM_TP_ID 	int(11) NOT NULL,
  CONSTRAINT STRM_FK_STRM_TP_ID FOREIGN KEY (FK_STRM_TP_ID) REFERENCES STRM_TP (ID),
  CONSTRAINT STRM_FK_STRM_PROC_ID FOREIGN KEY (FK_STRM_PROC_ID) REFERENCES STRM_PROC (ID),
  CONSTRAINT STRM_FK_FD_ID FOREIGN KEY (FK_FD_ID) REFERENCES FD (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- TODO We should store 2 int's CRT_HR and CRT_MS
--
CREATE INDEX STRM_CRT_MS_IDX ON STRM (CRT_MS);
CREATE INDEX STRM_FK_FD_ID_CRT_MS_IDX ON STRM (FK_FD_ID, CRT_MS);
CREATE INDEX STRM_FK_STRM_PROC_ID_CRT_MS_IDX ON STRM (FK_STRM_PROC_ID, CRT_MS);
CREATE INDEX STRM_FK_FD_ID_EFFECT_MS_IDX ON STRM (FK_FD_ID, EFFECT_MS);
CREATE INDEX STRM_PARNT_STRM_ID_IDX ON STRM (PARNT_STRM_ID);
CREATE INDEX STRM_STAT_IDX ON STRM (STAT);

--
-- Table structure for table strm_atr_key
--
CREATE TABLE STRM_ATR_KEY (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  FLD_TP			tinyint(4) NOT NULL,
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- By default monitor the following attributes in the database
--
insert into STRM_ATR_KEY
	( VER, CRT_DT, CRT_USER, UPD_DT, UPD_USER, NAME, FLD_TP )
values
	(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'RecRead', 5)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'RecWrite', 5)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'RecError', 5)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'RecWarn', 5)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'Node', 1)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'Feed', 1)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'StreamId', 4)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'ParentStreamId', 4)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'FileSize', 6)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'StreamSize', 6)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'StreamType', 1)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'Duration', 7)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'CreateTime', 3)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'EffectiveTime', 3)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'RecInfo', 5)
	,(1,  curdate(),  'upgrade', curdate(), 'upgrade', 'RecFatal', 5);

--
-- Table structure for table strm_atr_val
--
CREATE TABLE STRM_ATR_VAL (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) NOT NULL,
  VAL_STR			varchar(255) default NULL,
  VAL_NUM			bigint(20) default NULL,
  STRM_ID 			bigint(20) NOT NULL,
  STRM_ATR_KEY_ID 	int(11) default NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE INDEX STRM_ATR_VAL_STRM_ID_IDX ON STRM_ATR_VAL (STRM_ID);
CREATE INDEX STRM_ATR_VAL_CRT_MS_IDX ON STRM_ATR_VAL (CRT_MS);

--
-- Table structure for table strm_proc_filt_trac
--
CREATE TABLE STRM_PROC_FILT_TRAC (
  ID                int(11) auto_increment PRIMARY KEY,
  VER               tinyint(4) NOT NULL,
  MIN_STRM_ID       bigint(20) NOT NULL,
  MIN_EVT_ID        bigint(20) NOT NULL,
  MIN_STRM_CRT_MS   bigint(20) default NULL,
  MAX_STRM_CRT_MS   bigint(20) default NULL,
  STRM_CRT_MS       bigint(20) default NULL,
  LAST_POLL_MS      bigint(20) default NULL,
  LAST_POLL_TASK_CT int(11) default NULL,
  STAT				varchar(255) default NULL,
  STRM_CT			bigint(20) default NULL,
  EVT_CT			bigint(20) default NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table strm_proc_filt
--
CREATE TABLE STRM_PROC_FILT (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  DAT 				longtext NOT NULL,
  PRIOR 			int(11) NOT NULL,
  FK_STRM_PROC_ID 	int(11) NOT NULL,
  FK_STRM_PROC_FILT_TRAC_ID	int(11) NOT NULL,
  ENBL				bit(1) NOT NULL,
  CONSTRAINT STRM_PROC_FILT_FK_STRM_PROC_ID FOREIGN KEY (FK_STRM_PROC_ID) REFERENCES STRM_PROC (ID),
  CONSTRAINT STRM_PROC_FILT_FK_STRM_PROC_FILT_TRAC_ID FOREIGN KEY (FK_STRM_PROC_FILT_TRAC_ID) REFERENCES STRM_PROC_FILT_TRAC (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table strm_task
--
CREATE TABLE STRM_TASK (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) default NULL,
  END_TIME_MS 		bigint(20) default NULL,
  STAT 				tinyint(4) NOT NULL,
  STAT_MS 			bigint(20) default NULL,
  START_TIME_MS 	bigint(20) default NULL,
  FK_ND_ID 			int(11) default NULL,
  FK_STRM_ID 		bigint(20) NOT NULL,
  DAT 				longtext default NULL,
  FK_STRM_PROC_FILT_ID 	int(11) default NULL,
  CONSTRAINT 	STRM_TASK_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID),
  CONSTRAINT 	STRM_TASK_FK_STRM_PROC_FILT_ID FOREIGN KEY (FK_STRM_PROC_FILT_ID) REFERENCES STRM_PROC_FILT (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE INDEX STRM_TASK_STAT_IDX ON STRM_TASK (STAT);
CREATE INDEX STRM_TASK_FK_STRM_ID ON STRM_TASK(FK_STRM_ID);

--
-- Table structure for table strm_vol
--
CREATE TABLE STRM_VOL (
  ID 				bigint(20) auto_increment PRIMARY KEY,
  VER 				tinyint(4) NOT NULL,
  FK_STRM_ID 		bigint(20) NOT NULL,
  FK_VOL_ID 		int(11) NOT NULL,
  CONSTRAINT STRM_VOL_FK_VOL_ID FOREIGN KEY (FK_VOL_ID) REFERENCES VOL (ID),
  CONSTRAINT STRM_VOL_FK_STRM_ID FOREIGN KEY (FK_STRM_ID) REFERENCES STRM (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table txt_conv
--
CREATE TABLE TXT_CONV (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  CONV_TP 			tinyint(4) NOT NULL,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 		(FK_SYS_GRP_ID,NAME),
  CONSTRAINT 	TXT_CONV_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table xml_schema
--
CREATE TABLE XML_SCHEMA (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  DAT 				longtext,
  DEPRC 			bit(1) NOT NULL,
  SCHEMA_GRP 		varchar(255) default NULL,
  NS 				varchar(255) default NULL,
  SYS_ID 			varchar(255) default NULL,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT 		XML_SCHEMA_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table xslt
--
CREATE TABLE XSLT (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT XSLT_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table dash
--
CREATE TABLE DASH (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT 		DASH_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for resources
--
CREATE TABLE RES (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  DAT 				longtext
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table script
--
CREATE TABLE SCRIPT (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  FK_RES_ID			int(11) default NULL,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT SCRIPT_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID),
  CONSTRAINT SCRIPT_FK_RES_ID FOREIGN KEY (FK_RES_ID) REFERENCES RES (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table script dependencies
--
CREATE TABLE SCRIPT_DEP (
  FK_SCRIPT_ID 	    int(11) NOT NULL,
  DEP_FK_SCRIPT_ID 	int(11) NOT NULL,
  PRIMARY KEY  		(FK_SCRIPT_ID, DEP_FK_SCRIPT_ID),
  CONSTRAINT 		FK_SCRIPT_ID FOREIGN KEY (FK_SCRIPT_ID) REFERENCES SCRIPT (ID),
  CONSTRAINT 		DEP_FK_SCRIPT_ID FOREIGN KEY (DEP_FK_SCRIPT_ID) REFERENCES SCRIPT (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table visualisation
--
CREATE TABLE VIS (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  FUNC_NAME			varchar(255) default NULL,
  SETTINGS			longtext,
  FK_SCRIPT_ID		int(11) default NULL,
  FK_SYS_GRP_ID 	int(11) default NULL,
  UNIQUE 			(FK_SYS_GRP_ID,NAME),
  CONSTRAINT VIS_FK_SYS_GRP_ID FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID),
  CONSTRAINT VIS_FK_SCRIPT_ID FOREIGN KEY (FK_SCRIPT_ID) REFERENCES SCRIPT (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table query
--
CREATE TABLE QUERY (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  CRT_MS			bigint(20) NOT NULL,
  FK_SYS_USR_ID		int(11) NOT NULL,
  NAME 				varchar(255) default NULL,
  FK_DASH_ID		int(11) default NULL,
  DAT 				longtext,
  CONSTRAINT		QUERY_FK_SYS_USR_ID FOREIGN KEY (FK_SYS_USR_ID) REFERENCES SYS_USR (ID),
  CONSTRAINT		QUERY_FK_DASH_ID FOREIGN KEY (FK_DASH_ID) REFERENCES DASH (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for a statistic datasource
--
CREATE TABLE STAT_DAT_SRC(
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  ENGINE_NAME		varchar(20) NOT NULL,
  PRES				bigint(20) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  STAT_TP			tinyint(4) NOT NULL,
  ROLLUP_TP			tinyint(4) NOT NULL,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) NOT NULL,
  UNIQUE 			(NAME, ENGINE_NAME),
  CONSTRAINT 		STAT_DAT_SRC_FK_SYS_GRP_ID  FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;






/*
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Script to pre-load all the statistic data sources for the internal stats.
 * It adds the following system group structure
 *   Internal Statistics
 *     SQL
 *
 * This was initially a copy of mig_2_internal_stats.sql.  Any new internal stats
 * should be added to the sql below and then added as a new migration script.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */


/* Insert the system groups for the internal stats */

SET @internalStatsGrpName='Internal Statistics';
SET @sqlGrpName='SQL';

insert into `SYS_GRP`
	(ver, crt_dt, upd_dt, name, descrip, glob, comp_auth, fk_sys_grp_id)
values
	(0,utc_timestamp(),utc_timestamp(),@internalStatsGrpName,'','\0',0,NULL);

SET @internalStatsGrpId=(SELECT ID FROM SYS_GRP WHERE NAME = @internalStatsGrpName);

insert into `SYS_GRP`
	(ver, crt_dt, upd_dt, name, descrip, glob, comp_auth, fk_sys_grp_id)
values
	(0,utc_timestamp(),utc_timestamp(),@sqlGrpName,'','\0',0,@internalStatsGrpId);


SET @sqlGrpId=(SELECT ID FROM SYS_GRP WHERE NAME = @sqlGrpName AND fk_sys_grp_id = (SELECT ID FROM SYS_GRP WHERE NAME = @internalStatsGrpName));

/* Insert the statistic data sources into our new system groups
 *
 * All the SQL ones are enabled
 * */

INSERT INTO `STAT_DAT_SRC`
	(VER, UPD_DT, NAME, DESCRIP, ENGINE_NAME, PRES, ENBL, STAT_TP, ROLLUP_TP, DAT, FK_SYS_GRP_ID)
VALUES
	(0,utc_timestamp(),'Stream Task Queue Size','Stream Task Queue Size','SQL',60000,'',2,1,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n\n<data>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'PipelineStreamProcessor','This is my description','SQL',60000,'',1,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Feed</fieldName>\n   </field>\n   <field>\n      <fieldName>Pipeline</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-WriteEps','Node Status-WriteEps','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-ReadEps','Node Status-ReadEps','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-JvmNonHeapUsedMb','Node status - JvmNonHeapUsedMb','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-JvmNonHeapMaxMb','Node status - JvmNonHeapMaxMb','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-JvmNonHeapComittedMb','Node status - JvmNonHeapComittedMb','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-JvmHeapUsedMb','Node status - JvmHeapUsedMb','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-JvmHeapMaxMb','Node status - JvmHeapMaxMb','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-JvmHeapComittedMb','Node status - JvmHeapComittedMb','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuUser','Node Status-CpuUser','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuTotal','Node Status-CpuTotal','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuSystem','Node Status-CpuSystem','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuSoftIrq','Node Status-CpuSoftIrq','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuNice','Node Status-CpuNice','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuIrq','Node Status-CpuIrq','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuIoWait','Node Status-CpuIoWait','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Node Status-CpuIdle','Node Status-CpuIdle','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Meta Data-Streams Received','Meta Data-Streams Received','SQL',3600000,'',1,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>feed</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Meta Data-Stream Size','Meta Data-Stream Size','SQL',3600000,'',1,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>feed</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Meta Data-Bytes Received','Meta Data-Bytes Received','SQL',3600000,'',1,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>feed</fieldName>\n   </field>\n</data>\n',@sqlGrpId),
	(0,utc_timestamp(),'Benchmark-Cluster Test','Benchmark-Cluster Test','SQL',60000,'',2,2,'<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<data>\n   \n   <field>\n      <fieldName>Feed</fieldName>\n   </field>\n   <field>\n      <fieldName>Node</fieldName>\n   </field>\n   <field>\n      <fieldName>Type</fieldName>\n   </field>\n</data>\n',@sqlGrpId);


/* Script to pre-load all volume statistic data sources for the internal stats */
INSERT INTO `STAT_DAT_SRC`
	(VER, UPD_DT, NAME, DESCRIP, ENGINE_NAME, PRES, ENBL, STAT_TP, ROLLUP_TP, DAT, FK_SYS_GRP_ID)
VALUES
	(0,utc_timestamp(),'Volume Limit','Volume Limit','SQL',60000,'',2,2,'<?xml version="1.1" encoding="UTF-8"?><data><field><fieldName>Id</fieldName></field><field><fieldName>Node</fieldName></field><field><fieldName>Path</fieldName></field></data>',@sqlGrpId),
	(0,utc_timestamp(),'Volume Used','Volume Used','SQL',60000,'',2,2,'<?xml version="1.1" encoding="UTF-8"?><data><field><fieldName>Id</fieldName></field><field><fieldName>Node</fieldName></field><field><fieldName>Path</fieldName></field></data>',@sqlGrpId),
	(0,utc_timestamp(),'Volume Free','Volume Free','SQL',60000,'',2,2,'<?xml version="1.1" encoding="UTF-8"?><data><field><fieldName>Id</fieldName></field><field><fieldName>Node</fieldName></field><field><fieldName>Path</fieldName></field></data>',@sqlGrpId),
	(0,utc_timestamp(),'Volume Total','Volume Total','SQL',60000,'',2,2,'<?xml version="1.1" encoding="UTF-8"?><data><field><fieldName>Id</fieldName></field><field><fieldName>Node</fieldName></field><field><fieldName>Path</fieldName></field></data>',@sqlGrpId),
	(0,utc_timestamp(),'Volume Use%','Volume Use%','SQL',60000,'',2,2,'<?xml version="1.1" encoding="UTF-8"?><data><field><fieldName>Id</fieldName></field><field><fieldName>Node</fieldName></field><field><fieldName>Path</fieldName></field></data>',@sqlGrpId);


	/*
	 * End of internal stats creation
	 *
	 */
