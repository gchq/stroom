-- ------------------------------------------------------------------------
-- Copyright 2021 Crown Copyright
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
-- Create the token table
--
CREATE TABLE IF NOT EXISTS token (
    id                int(11) NOT NULL AUTO_INCREMENT,
    version           int(11) NOT NULL,
    create_time_ms    bigint(20) NOT NULL,
    create_user       varchar(255) NOT NULL,
    update_time_ms    bigint(20) NOT NULL,
    update_user       varchar(255) NOT NULL,
    fk_account_id     int(11) NOT NULL,
    fk_token_type_id  int(11) NOT NULL,
    data              longtext,
    expires_on_ms     bigint(20) DEFAULT NULL,
    comments          longtext,
    enabled           tinyint(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY token_fk_account_id (fk_account_id),
    KEY token_fk_token_type_id (fk_token_type_id),
    CONSTRAINT token_fk_account_id
        FOREIGN KEY (fk_account_id)
        REFERENCES account (id),
    CONSTRAINT token_fk_token_type_id
        FOREIGN KEY (fk_token_type_id)
        REFERENCES token_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the token table
--
DROP PROCEDURE IF EXISTS identity_copy_old_auth_tokens;

DELIMITER //

CREATE PROCEDURE identity_copy_old_auth_tokens ()
BEGIN
    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_AUTH_tokens') THEN

        INSERT INTO token (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            fk_account_id,
            fk_token_type_id,
            data,
            expires_on_ms,
            comments,
            enabled)
        SELECT
            id,
            1,
            UNIX_TIMESTAMP(issued_on) * 1000,
            issued_by_user,
            IFNULL(UNIX_TIMESTAMP(updated_on) * 1000, UNIX_TIMESTAMP(issued_on) * 1000),
            IFNULL(updated_by_user, issued_by_user),
            user_id,
            token_type_id,
            token,
            UNIX_TIMESTAMP(expires_on) * 1000,
            comments,
            enabled
        FROM OLD_AUTH_tokens
        WHERE id > (SELECT COALESCE(MAX(id), 0) FROM token)
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT
            CONCAT(
                'ALTER TABLE token AUTO_INCREMENT = ',
                COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM token;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//

DELIMITER ;

-- idempotent
CALL identity_copy_old_auth_tokens();

DROP PROCEDURE identity_copy_old_auth_tokens;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
