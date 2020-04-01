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
    UNIQUE                (name, is_group),
    CONSTRAINT            stroom_user_uk_uuid 
        UNIQUE INDEX stroom_user_uuid_index (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS stroom_user_group (
    id                    bigint(20) NOT NULL AUTO_INCREMENT,
    user_uuid             varchar(255) NOT NULL,
    group_uuid            varchar(255) NOT NULL,
    PRIMARY KEY           (id),
    CONSTRAINT stroom_user_group_fk_user_uuid 
        FOREIGN KEY (user_uuid) 
        REFERENCES stroom_user (uuid),
    CONSTRAINT stroom_user_group_fk_group_uuid
        FOREIGN KEY (group_uuid) 
        REFERENCES stroom_user (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS app_permission (
    id                    bigint(20) NOT NULL AUTO_INCREMENT,
    user_uuid             varchar(255) NOT NULL,
    permission            varchar(255) NOT NULL,
    PRIMARY KEY           (id),
    CONSTRAINT app_permission_fk_user_uuid
        FOREIGN KEY (user_uuid) 
        REFERENCES stroom_user (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS doc_permission (
    id                    bigint(20) NOT NULL AUTO_INCREMENT,
    user_uuid             varchar(255) NOT NULL,
    doc_uuid              varchar(255) NOT NULL,
    permission            varchar(255) NOT NULL,
    PRIMARY KEY           (id),
    CONSTRAINT doc_permission_fk_user_uuid
        FOREIGN KEY (user_uuid) 
        REFERENCES stroom_user (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
