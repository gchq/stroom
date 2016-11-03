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
-- Security
----------------------------------------

-- User
CREATE TABLE USR (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME				varchar(255) NOT NULL,
  UUID 				varchar(255) NOT NULL,
  GRP               bit(1) NOT NULL,
  STAT				tinyint(4) NOT NULL,
  CUR_LOGIN_FAIL	smallint(6) NOT NULL,
  LOGIN_EXPIRY		bit(1) NOT NULL,
  LAST_LOGIN_MS		bigint(20) DEFAULT NULL,
  LOGIN_VALID_MS	bigint(20) DEFAULT NULL,
  PASS_EXPIRY_MS	bigint(20) DEFAULT NULL,
  PASS_HASH			varchar(255) DEFAULT NULL,
  TOTL_LOGIN_FAIL	smallint(6) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME, GRP),
  UNIQUE            (UUID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- User Group User
CREATE TABLE USR_GRP_USR (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  GRP_UUID 			varchar(255) NOT NULL,
  USR_UUID 			varchar(255) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(GRP_UUID, USR_UUID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Permission
CREATE TABLE PERM (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  NAME 			    varchar(255) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Application Permission
CREATE TABLE APP_PERM (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  USR_UUID  	    varchar(255) NOT NULL,
  FK_PERM_ID        int(11) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(USR_UUID, FK_PERM_ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Document Permission
CREATE TABLE DOC_PERM (
  ID 				bigint(20) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  USR_UUID  	    varchar(255) NOT NULL,
  DOC_TP            varchar(255) NOT NULL,
  DOC_UUID          varchar(255) NOT NULL,
  PERM              varchar(255) NOT NULL,
  PRIMARY KEY       (ID),
  UNIQUE 			(USR_UUID, DOC_TP, DOC_UUID, PERM)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;