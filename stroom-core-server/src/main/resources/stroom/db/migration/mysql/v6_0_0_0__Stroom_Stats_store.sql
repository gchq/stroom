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


CREATE TABLE STROOM_STATS_STORE(
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_DT 			timestamp NULL DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_DT 			timestamp NULL DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  NAME 				varchar(255) NOT NULL,
  DESCRIP 			longtext,
  PRES		        varchar(255) NOT NULL,
  ENBL 				bit(1) NOT NULL,
  STAT_TP			tinyint(4) NOT NULL,
  ROLLUP_TP			tinyint(4) NOT NULL,
  DAT 				longtext,
  FK_SYS_GRP_ID 	int(11) NOT NULL,
  UNIQUE 			(NAME),
  CONSTRAINT 		STROOM_STATS_STORE_FK_SYS_GRP_ID  FOREIGN KEY (FK_SYS_GRP_ID) REFERENCES SYS_GRP (ID)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
