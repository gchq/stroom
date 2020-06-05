--
-- Table structure for table policy
--
CREATE TABLE POLICY (
  ID 				int(11) NOT NULL AUTO_INCREMENT,
  VER				tinyint(4) NOT NULL,
  CRT_MS 			bigint(20) DEFAULT NULL,
  CRT_USER			varchar(255) DEFAULT NULL,
  UPD_MS 			bigint(20) DEFAULT NULL,
  UPD_USER 			varchar(255) DEFAULT NULL,
  NAME				varchar(255) NOT NULL,
  DAT 				longtext,
  PRIMARY KEY       (ID),
  UNIQUE 			(NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;