-- ------------------------------------------------------------------------
-- Copyright 2020 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the permission tables
--
CREATE TABLE IF NOT EXISTS stroom_user (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  version               int(11) NOT NULL,
  create_time_ms        bigint(20) NOT NULL,
  create_user           varchar(255) NOT NULL,
  update_time_ms        bigint(20) NOT NULL,
  update_user           varchar(255) NOT NULL,
  name                  varchar(255) NOT NULL,
  uuid                  varchar(255) NOT NULL,
  is_group              bit(1) NOT NULL,
  enabled               bit(1) NOT NULL,
  PRIMARY KEY           (id),
  UNIQUE 			    (name, is_group),
  CONSTRAINT            stroom_user_uk_uuid UNIQUE INDEX stroom_user_uuid_index (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS stroom_user_group (
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
-- Copy data into the permissions tables
--
DROP PROCEDURE IF EXISTS copy_security;
DELIMITER //
CREATE PROCEDURE copy_security ()
BEGIN

  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'USR') THEN

    SET @insert_sql=''
        ' INSERT INTO stroom_user (id, version, create_time_ms, create_user, update_time_ms, update_user, name, uuid, is_group, enabled)'
        ' SELECT ID, 1, CRT_MS, CRT_USER, UPD_MS, UPD_USER, NAME, UUID, GRP, (CASE STAT WHEN 0 THEN true ELSE false END)'
        ' FROM USR'
        ' WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM stroom_user)'
        ' ORDER BY ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE stroom_user AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM stroom_user;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;

  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'USR_GRP_USR') THEN

    SET @insert_sql=''
        ' INSERT INTO stroom_user_group (user_uuid, group_uuid)'
        ' SELECT USR_UUID, GRP_UUID'
        ' FROM USR_GRP_USR'
        ' WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM stroom_user_group)'
        ' ORDER BY ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE stroom_user_group AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM stroom_user_group;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;

  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'DOC_PERM') THEN

    SET @insert_sql=''
        ' INSERT INTO doc_permission (user_uuid, doc_uuid, permission)'
        ' SELECT USR_UUID, DOC_UUID, PERM'
        ' FROM DOC_PERM'
        ' WHERE ID > (SELECT COALESCE(MAX(id), 0) FROM doc_permission)'
        ' ORDER BY ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE doc_permission AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM doc_permission;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;
  
  IF EXISTS (
      SELECT TABLE_NAME
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_NAME = 'APP_PERM') THEN

    SET @insert_sql=''
        ' INSERT INTO app_permission (user_uuid, permission)'
        ' SELECT ap.USR_UUID, p.NAME'
        ' FROM APP_PERM ap'
        ' JOIN PERM p ON (p.ID = ap.FK_PERM_ID)'
        ' WHERE ap.ID > (SELECT COALESCE(MAX(id), 0) FROM app_permission)'
        ' ORDER BY ap.ID;';
    PREPARE insert_stmt FROM @insert_sql;
    EXECUTE insert_stmt;

    -- Work out what to set our auto_increment start value to
    SELECT CONCAT('ALTER TABLE app_permission AUTO_INCREMENT = ', COALESCE(MAX(id) + 1, 1))
    INTO @alter_table_sql
    FROM app_permission;

    PREPARE alter_table_stmt FROM @alter_table_sql;
    EXECUTE alter_table_stmt;
  END IF;

END//
DELIMITER ;
CALL copy_security();
DROP PROCEDURE copy_security;

SET SQL_NOTES=@OLD_SQL_NOTES;
