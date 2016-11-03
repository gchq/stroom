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
-- Processing
----------------------------------------

-- Job
CREATE TABLE JB (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Job Node
CREATE TABLE JB_ND (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  JB_TP 			tinyint(4) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  SCHEDULE 			varchar(255) DEFAULT NULL,
  TASK_LMT 			int(11) NOT NULL,
  FK_JB_ID 			int(11) NOT NULL,
  FK_ND_ID 			int(11) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_ND_ID,FK_JB_ID),
  CONSTRAINT 		JB_ND_FK_JB_ID FOREIGN KEY (FK_JB_ID) REFERENCES JB (ID),
  CONSTRAINT 		JB_ND_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Stream Processor
CREATE TABLE STRM_PROC (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  TASK_TP 			varchar(255) DEFAULT NULL,
  FK_PIPE_ID 		int(11) DEFAULT NULL,
  ENBL				bit(1) NOT NULL,
  PRIMARY KEY       (ID),
  CONSTRAINT        STRM_PROC_FK_PIPE_ID FOREIGN KEY (FK_PIPE_ID) REFERENCES PIPE (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Stream Processor Filter Tracker
CREATE TABLE STRM_PROC_FILT_TRAC (
  ID                int(11) NOT NULL AUTO_INCREMENT,
  VER               tinyint(4) NOT NULL,
  MIN_STRM_ID       bigint(20) NOT NULL,
  MIN_EVT_ID        bigint(20) NOT NULL,
  MIN_STRM_CRT_MS   bigint(20) DEFAULT NULL,
  MAX_STRM_CRT_MS   bigint(20) DEFAULT NULL,
  STRM_CRT_MS       bigint(20) DEFAULT NULL,
  LAST_POLL_MS      bigint(20) DEFAULT NULL,
  LAST_POLL_TASK_CT int(11) DEFAULT NULL,
  STAT				varchar(255) DEFAULT NULL,
  STRM_CT			bigint(20) DEFAULT NULL,
  EVT_CT			bigint(20) DEFAULT NULL,
  PRIMARY KEY       (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Stream Processor Filter
CREATE TABLE STRM_PROC_FILT (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  DAT 				longtext NOT NULL,
  PRIOR 			int(11) NOT NULL,
  FK_STRM_PROC_ID 	int(11) NOT NULL,
  FK_STRM_PROC_FILT_TRAC_ID	int(11) NOT NULL,
  ENBL				bit(1) NOT NULL,
  PRIMARY KEY       (ID),
  CONSTRAINT STRM_PROC_FILT_FK_STRM_PROC_ID FOREIGN KEY (FK_STRM_PROC_ID) REFERENCES STRM_PROC (ID),
  CONSTRAINT STRM_PROC_FILT_FK_STRM_PROC_FILT_TRAC_ID FOREIGN KEY (FK_STRM_PROC_FILT_TRAC_ID) REFERENCES STRM_PROC_FILT_TRAC (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;