/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

----------------------------------------
-- Data Store
----------------------------------------

-- Stream Type
CREATE TABLE STRM_TP (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  EXT 				varchar(255) NOT NULL,
  PURP 				tinyint(4) NOT NULL,
  PATH 				varchar(255) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Feed
CREATE TABLE FD (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  STAT 				tinyint(4) NOT NULL,
  CLS 				varchar(255) DEFAULT NULL,
  CTX_ENC 			varchar(255) DEFAULT NULL,
  ENC 				varchar(255) DEFAULT NULL,
  REF				bit(1) NOT NULL,
  RETEN_DAY_AGE 	int(11) DEFAULT NULL,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  FK_STRM_TP_ID 	int(11) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE  			(NAME),
  UNIQUE  			(UUID),
  CONSTRAINT 		FD_FK_STRM_TP_ID FOREIGN KEY (FK_STRM_TP_ID) REFERENCES STRM_TP (ID),
  CONSTRAINT 		FD_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Volume State
CREATE TABLE VOL_STATE (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  BYTES_USED		bigint(20) DEFAULT NULL,
  BYTES_FREE		bigint(20) DEFAULT NULL,
  BYTES_TOTL		bigint(20) DEFAULT NULL,
  STAT_MS 			bigint(20) DEFAULT NULL,
  PRIMARY KEY       (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Volume
CREATE TABLE VOL (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  PATH 				varchar(255) NOT NULL,
  IDX_STAT 			tinyint(4) NOT NULL,
  STRM_STAT 		tinyint(4) NOT NULL,
  VOL_TP 			tinyint(4) NOT NULL,
  BYTES_LMT			bigint(20) DEFAULT NULL,
  FK_ND_ID 			int(11) NOT NULL,
  FK_VOL_STATE_ID 	int(11) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_VOL_STATE_ID),
  UNIQUE 			(FK_ND_ID, PATH),
  CONSTRAINT 		VOL_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID),
  CONSTRAINT 		VOL_STAT_FK_VOL_STATE_ID FOREIGN KEY (FK_VOL_STATE_ID) REFERENCES VOL_STATE (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Stream
CREATE TABLE STRM (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER 				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) NOT NULL,
  EFFECT_MS 		bigint(20) DEFAULT NULL,
  PARNT_STRM_ID 	bigint(20) DEFAULT NULL,
  STAT 				tinyint(4) NOT NULL,
  STAT_MS           bigint(20) DEFAULT NULL,
  STRM_TASK_ID      bigint(20) DEFAULT NULL,
  FK_FD_ID 			int(11) NOT NULL,
  FK_STRM_PROC_ID 	int(11) DEFAULT NULL,
  FK_STRM_TP_ID 	int(11) NOT NULL,
  PRIMARY KEY       (ID),
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


-- Stream Volume
CREATE TABLE STRM_VOL (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER 				tinyint(4) NOT NULL,
  FK_STRM_ID 		bigint(20) NOT NULL,
  FK_VOL_ID 		int(11) NOT NULL,
  PRIMARY KEY       (ID),
  CONSTRAINT STRM_VOL_FK_STRM_ID FOREIGN KEY (FK_STRM_ID) REFERENCES STRM (ID),
  CONSTRAINT STRM_VOL_FK_VOL_ID FOREIGN KEY (FK_VOL_ID) REFERENCES VOL (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Stream Attribute Key
CREATE TABLE STRM_ATR_KEY (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  FLD_TP			tinyint(4) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Stream Attribute Value
CREATE TABLE STRM_ATR_VAL (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER 				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) NOT NULL,
  VAL_STR			varchar(255) DEFAULT NULL,
  VAL_NUM			bigint(20) DEFAULT NULL,
  STRM_ID 			bigint(20) NOT NULL,
  STRM_ATR_KEY_ID 	int(11) DEFAULT NULL,
  PRIMARY KEY       (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE INDEX STRM_ATR_VAL_STRM_ID_IDX ON STRM_ATR_VAL (STRM_ID);
CREATE INDEX STRM_ATR_VAL_CRT_MS_IDX ON STRM_ATR_VAL (CRT_MS);





-------
-- TODO : Move back to processing and reorder these (V2 data store, V3 pipelines, V4 processing) once the link from stream to stream processor has been broken
-------

-- Stream Task
CREATE TABLE STRM_TASK (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER 				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) DEFAULT NULL,
  END_TIME_MS 		bigint(20) DEFAULT NULL,
  STAT 				tinyint(4) NOT NULL,
  STAT_MS 			bigint(20) DEFAULT NULL,
  START_TIME_MS 	bigint(20) DEFAULT NULL,
  FK_ND_ID 			int(11) DEFAULT NULL,
  FK_STRM_ID 		bigint(20) NOT NULL,
  DAT 				longtext DEFAULT NULL,
  FK_STRM_PROC_FILT_ID 	int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  CONSTRAINT 	    STRM_TASK_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID),
  CONSTRAINT 	    STRM_TASK_FK_STRM_PROC_FILT_ID FOREIGN KEY (FK_STRM_PROC_FILT_ID) REFERENCES STRM_PROC_FILT (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE INDEX STRM_TASK_STAT_IDX ON STRM_TASK (STAT);