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
-- Statistics
----------------------------------------

--
-- Table structure for a statistic datasource
--
CREATE TABLE STAT_DAT_SRC(
  ID 				    int(11) NOT NULL AUTO_INCREMENT,
  VER				    tinyint(4) NOT NULL,
  CRT_DT 			    timestamp NULL DEFAULT NULL,
  CRT_USER			    varchar(255),
  UPD_DT 			    timestamp NULL DEFAULT NULL,
  UPD_USER 			    varchar(255),
  NAME 				    varchar(255) NOT NULL,
  UUID                  varchar(255) NOT NULL,
  DESCRIP 			    longtext,
  ENGINE_NAME		    varchar(20) NOT NULL,
  PRES			       	bigint(20) NOT NULL,
  ENBL 			    	bit(1) NOT NULL,
  STAT_TP		    	tinyint(4) NOT NULL,
  ROLLUP_TP		    	tinyint(4) NOT NULL,
  DAT 			    	longtext,
  FK_FOLDER_ID 	        int(11) NOT NULL,
  PRIMARY KEY           (ID),
  UNIQUE 			    (NAME, ENGINE_NAME),
  UNIQUE                (UUID),
  CONSTRAINT 		    STAT_DAT_SRC_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Table structure for table sql_stat_key
--
CREATE TABLE SQL_STAT_KEY (
  ID 				    bigint(20) NOT NULL AUTO_INCREMENT,
  VER 				    tinyint(4) NOT NULL,
  NAME 				    varchar(766) NOT NULL,
  PRIMARY KEY           (ID),
  UNIQUE 			    (NAME)
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
  PRIMARY KEY           (FK_SQL_STAT_KEY_ID, TIME_MS, VAL_TP, PRES),
  CONSTRAINT            SQL_STAT_VAL_FK_STAT_KEY_ID FOREIGN KEY (FK_SQL_STAT_KEY_ID) REFERENCES SQL_STAT_KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- used for deletions of old data where the FK is not involved
CREATE INDEX SQL_STAT_VAL_TIME_MS ON SQL_STAT_VAL (TIME_MS);

--
-- Table structure for table sql_stat_val_src
--
CREATE TABLE SQL_STAT_VAL_SRC (
  TIME_MS			    bigint(20) NOT NULL,
  NAME 				    varchar(766) NOT NULL,
  VAL_TP 			    tinyint(4) NOT NULL,
  VAL				    bigint(20) NOT NULL,
  PROCESSING            bit(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1;