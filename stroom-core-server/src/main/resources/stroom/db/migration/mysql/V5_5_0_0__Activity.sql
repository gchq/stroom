--
-- Table structure for table activity
--
CREATE TABLE ACTIVITY (
  ID 				int(11) auto_increment PRIMARY KEY,
  VER				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) DEFAULT NULL,
  CRT_USER			varchar(255) default NULL,
  UPD_MS 			bigint(20) DEFAULT NULL,
  UPD_USER 			varchar(255) default NULL,
  USER_ID 		    varchar(255) NOT NULL,
  JSON 				longtext
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
