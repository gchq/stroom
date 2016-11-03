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
-- Pipelines
----------------------------------------

-- Text Converter
CREATE TABLE TXT_CONV (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID              varchar(255) NOT NULL,
  DESCRIP 			longtext,
  CONV_TP 			tinyint(4) NOT NULL,
  DAT 				longtext,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE 			(UUID),
  CONSTRAINT 	    TXT_CONV_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- XSLT
CREATE TABLE XSLT (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID              varchar(255) NOT NULL,
  DESCRIP 			longtext,
  DAT 				longtext,
  FK_FOLDER_ID 	    int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE 			(UUID),
  CONSTRAINT XSLT_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- XML Schema
CREATE TABLE XML_SCHEMA (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID              varchar(255) NOT NULL,
  DESCRIP 			longtext,
  DAT 				longtext,
  DEPRC 			bit(1) NOT NULL,
  SCHEMA_GRP 		varchar(255) DEFAULT NULL,
  NS 				varchar(255) DEFAULT NULL,
  SYSTEM_ID 		varchar(255) DEFAULT NULL,
  FK_FOLDER_ID  	int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE 			(UUID),
  CONSTRAINT 		XML_SCHEMA_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Pipeline
CREATE TABLE PIPE (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID              varchar(255) NOT NULL,
  DESCRIP 			longtext,
  PARNT_PIPE        longtext,
  DAT 				longtext,
  PIPE_TP 			varchar(255) DEFAULT NULL,
  FK_FOLDER_ID  	int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE 			(UUID),
  CONSTRAINT 		PIPE_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;