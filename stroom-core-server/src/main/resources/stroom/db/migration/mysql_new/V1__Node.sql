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
-- General
----------------------------------------

-- Folder
CREATE TABLE FOLDER (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER 				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER  		varchar(255) DEFAULT NULL,
  UPD_DT  			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  UUID              varchar(255) NOT NULL,
  FK_FOLDER_ID  	int(11) DEFAULT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(FK_FOLDER_ID, NAME),
  UNIQUE 			(UUID),
  CONSTRAINT  		FOLDER_FK_FOLDER_ID FOREIGN KEY (FK_FOLDER_ID) REFERENCES FOLDER (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Global Property
CREATE TABLE GLOB_PROP (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME 				varchar(255) NOT NULL,
  VAL 				longtext NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE  			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Cluster Lock
CREATE TABLE CLSTR_LK (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  NAME 				varchar(255) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


----------------------------------------
-- Node
----------------------------------------

-- Rack
CREATE TABLE RK (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME				varchar(255) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Node
CREATE TABLE ND (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  CLSTR_URL			varchar(255) DEFAULT NULL,
  NAME				varchar(255) NOT NULL,
  PRIOR				smallint(6) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  FK_RK_ID 			int(11) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE			(NAME),
  CONSTRAINT 		ND_FK_RK_ID FOREIGN KEY (FK_RK_ID) REFERENCES RK (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;