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
-- Indexing
----------------------------------------

-- Index
CREATE TABLE IDX (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID              varchar(255) NOT NULL,
  DESCRIP 			longtext,
  MAX_DOC			int(11) NOT NULL,
  MAX_SHRD			int(11) NOT NULL,
  PART_BY			tinyint(4) DEFAULT NULL,
  PART_SZ			int(11) NOT NULL,
  RETEN_DAY_AGE 	int(11) DEFAULT NULL,
  FLDS 				longtext,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE            (UUID),
  CONSTRAINT 		IDX_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Index Volume
CREATE TABLE IDX_VOL (
  FK_IDX_ID 	    int(11) NOT NULL,
  FK_VOL_ID 	    int(11) NOT NULL,
  PRIMARY KEY  		(FK_IDX_ID, FK_VOL_ID),
  CONSTRAINT 		VOL_FK_IDX_ID FOREIGN KEY (FK_IDX_ID) REFERENCES IDX (ID),
  CONSTRAINT 		IDX_FK_VOL_ID FOREIGN KEY (FK_VOL_ID) REFERENCES VOL (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Index Shard
CREATE TABLE IDX_SHRD (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  CMT_DOC_CT 		int(11) DEFAULT NULL,
  CMT_DUR_MS 		bigint(20) DEFAULT NULL,
  CMT_MS 			bigint(20) DEFAULT NULL,
  DOC_CT 			int(11) NOT NULL,
  FILE_SZ 			bigint(20) DEFAULT NULL,
  STAT 				tinyint(4) NOT NULL,
  FK_IDX_ID	 		int(11) NOT NULL,
  FK_ND_ID 			int(11) NOT NULL,
  PART				varchar(255) NOT NULL,
  PART_FROM_DT 		timestamp NULL DEFAULT NULL,
  PART_TO_DT 		timestamp NULL DEFAULT NULL,
  FK_VOL_ID 		int(11) NOT NULL,
  IDX_VER			varchar(255) DEFAULT NULL,
  PRIMARY KEY       (ID),
  CONSTRAINT 		IDX_SHRD_FK_VOL_ID FOREIGN KEY (FK_VOL_ID) REFERENCES VOL (ID),
  CONSTRAINT 		IDX_SHRD_FK_IDX_ID FOREIGN KEY (FK_IDX_ID) REFERENCES IDX (ID),
  CONSTRAINT 		IDX_SHRD_FK_ND_ID FOREIGN KEY (FK_ND_ID) REFERENCES ND (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;