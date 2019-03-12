--
-- Create the explorer tables
--
CREATE TABLE IF NOT EXISTS stroom_user (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  name                  varchar(255) NOT NULL,
  uuid                  varchar(255) NOT NULL,
  is_group              bit(1) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE 			    (name, is_group),
  CONSTRAINT            user_uk_uuid UNIQUE INDEX usr_uuid_index (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS stroom_user_groups (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  user_uuid             varchar(255) NOT NULL,
  group_uuid            varchar(255) NOT NULL,
  FOREIGN KEY (user_uuid) REFERENCES stroom_user (uuid),
  FOREIGN KEY (group_uuid) REFERENCES stroom_user (uuid),
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS app_permission (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  user_uuid             varchar(255) NOT NULL,
  permission            varchar(255) NOT NULL,
  FOREIGN KEY (user_uuid) REFERENCES stroom_user (uuid),
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS doc_permission (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  user_uuid             varchar(255) NOT NULL,
  doc_uuid              varchar(255) NOT NULL,
  permission            varchar(255) NOT NULL,
  FOREIGN KEY (user_uuid) REFERENCES stroom_user (uuid),
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the explorer table
--
DROP PROCEDURE IF EXISTS copy_security;
DELIMITER //
CREATE PROCEDURE copy_security ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'USR' > 0) THEN
        INSERT INTO stroom_user (uuid, name, is_group )
        SELECT uuid, name, grp
        FROM USR;
          -- TODO update auto-increment, see V7_0_0_1__config.sql as an example
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'USR_GRP_USR' > 0) THEN
        INSERT INTO stroom_user_groups (user_uuid, group_uuid)
        SELECT grp_uuid, usr_uuid
        FROM USR_GRP_USR;
          -- TODO update auto-increment, see V7_0_0_1__config.sql as an example
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'DOC_PERM' > 0) THEN
        INSERT INTO doc_permission (user_uuid, doc_uuid, permission)
        SELECT usr_uuid, doc_uuid, perm
        FROM DOC_PERM;
          -- TODO update auto-increment, see V7_0_0_1__config.sql as an example
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'APP_PERM' > 0) THEN
        INSERT INTO app_permission (user_uuid, permission)
        SELECT usr_uuid, name
        FROM APP_PERM INNER JOIN PERM on APP_PERM.FK_PERM_ID = PERM.ID;
          -- TODO update auto-increment, see V7_0_0_1__config.sql as an example
    END IF;
END//
DELIMITER ;
CALL copy_security();
DROP PROCEDURE copy_security;