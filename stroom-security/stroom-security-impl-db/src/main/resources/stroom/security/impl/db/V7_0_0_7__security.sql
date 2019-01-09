--
-- Create the explorer tables
--
CREATE TABLE IF NOT EXISTS app_permission_name (
  id                    bigint(20) NOT NULL,
  version               tinyint(4) NOT NULL,
  name                  varchar(255) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE KEY            (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS app_permission (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  version               tinyint(4) NOT NULL,
  user_uuid             varchar(255) NOT NULL,
  fk_app_permission_id  bigint(20) NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS doc_permission (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  version               tinyint(4) NOT NULL,
  user_uuid             varchar(255) NOT NULL,
  doc_type              varchar(255) NOT NULL,
  doc_uuid              varchar(255) NOT NULL,
  app_permission        varchar(255) NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS stroom_user (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  version               tinyint(4) NOT NULL,
  name                  varchar(255) NOT NULL,
  uuid                  varchar(255) NOT NULL,
  is_group              bit(1) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE 			    (name, is_group),
  CONSTRAINT            user_uk_uuid UNIQUE INDEX usr_uuid_index (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS stroom_user_groups (
  id                    bigint(20) NOT NULL AUTO_INCREMENT,
  version               tinyint(4) NOT NULL,
  user_uuid             varchar(255) NOT NULL,
  group_uuid            varchar(255) NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the explorer table
--
DROP PROCEDURE IF EXISTS copy;
DELIMITER //
CREATE PROCEDURE copy ()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'USR' > 0) THEN
        INSERT INTO stroom_user (id, version, uuid, name, is_group )
        SELECT id, version, uuid, name, is_group
        FROM USR;
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'USR_GRP_USR' > 0) THEN
        INSERT INTO stroom_user_groups (id, version, user_uuid, group_uuid )
        SELECT id, version, grp_uuid, usr_uuid
        FROM USR_GRP_USR;
    END IF;
END//
DELIMITER ;
CALL copy();
DROP PROCEDURE copy;