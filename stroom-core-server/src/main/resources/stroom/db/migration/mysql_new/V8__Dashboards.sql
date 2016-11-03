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
-- Dashboards
----------------------------------------

-- Dashboard
CREATE TABLE DASH (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID 				varchar(255) DEFAULT NULL,
  DAT 				longtext,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE            (UUID),
  CONSTRAINT 		DASH_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Resource
CREATE TABLE RES (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  DAT 				longtext,
  PRIMARY KEY       (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Script
CREATE TABLE SCRIPT (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID 				varchar(255) DEFAULT NULL,
  DESCRIP 			longtext,
  DEP   		    longtext,
  FK_RES_ID			int(11) DEFAULT NULL,
  FK_FOLDER_ID  	int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE            (UUID),
  CONSTRAINT        SCRIPT_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID),
  CONSTRAINT        SCRIPT_FK_RES_ID FOREIGN KEY (FK_RES_ID) REFERENCES RES (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Vis
CREATE TABLE VIS (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID 				varchar(255) DEFAULT NULL,
  DESCRIP 			longtext,
  FUNC_NAME			varchar(255) DEFAULT NULL,
  SCRIPT		    longtext,
  SETTINGS			longtext,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE            (UUID),
  CONSTRAINT        VIS_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Dictionary
CREATE TABLE DICT (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID 				varchar(255) DEFAULT NULL,
  DAT 				longtext,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE            (UUID),
  CONSTRAINT 		DICT_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Query
CREATE TABLE QUERY (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) DEFAULT NULL,
  UUID 				varchar(255) DEFAULT NULL,
  FK_DASH_ID		int(11) DEFAULT NULL,
  DAT 				longtext,
  FAVOURITE         bit(1) NOT NULL,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE            (UUID),
  CONSTRAINT		QUERY_FK_DASH_ID FOREIGN KEY (FK_DASH_ID) REFERENCES DASH (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;