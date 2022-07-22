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
-- Copy data into the permissions tables
--
DROP PROCEDURE IF EXISTS legacy_migrate_usr;
DELIMITER //
CREATE PROCEDURE legacy_migrate_usr ()
BEGIN
    -- OLD_USR should have been created in V07_00_00_2102__copy_USR_to_stroom_user.sql

    -- This migrates users from the USR table from a version prior to v6.
    -- v6 systems will get handled by
    -- stroom-security/stroom-security-identity-db/src/main/resources/stroom/security/identity/db/migration/V07_00_00_005__account.sql
    -- and https://github.com/gchq/stroom/blob/v7.0-beta.192/scripts/v7_auth_db_table_rename.sql
    -- If OLD_AUTH_users is present that means it is a v6=>v7+ migration
    -- which is covered by V07_00_00_005__account.sql

    IF EXISTS (
            SELECT NULL
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_NAME = 'OLD_USR')
       AND NOT EXISTS (
           SELECT NULL
           FROM INFORMATION_SCHEMA.TABLES
           WHERE TABLE_SCHEMA = database()
           AND TABLE_NAME = 'OLD_AUTH_users') THEN

        INSERT INTO account (
            id,
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            user_id,
            email,
            password_hash,
            password_last_changed_ms,
            first_name,
            last_name,
            comments,
            login_count,
            login_failures,
            last_login_ms,
            reactivated_ms,
            force_password_change,
            never_expires,
            enabled,
            inactive,
            locked,
            processing_account)
        SELECT
            null, -- id, (auto increment)
            1, -- version
            COALESCE(U.CRT_MS, UNIX_TIMESTAMP() * 1000), -- create_time_ms
            COALESCE(U.CRT_USER, "Flyway Migration"), -- create_user
            COALESCE(U.UPD_MS, U.CRT_MS, UNIX_TIMESTAMP() * 1000), -- update_time_ms
            COALESCE(U.UPD_USER, U.CRT_USER, "Flyway Migration"), -- update_user
            U.NAME, -- user_id
            null, -- email
            U.PASS_HASH, -- password_hash
            null, -- password_last_changed_ms
            null, -- first_name
            null, -- last_name
            null, -- comments
            0, -- login_count
            COALESCE(U.CUR_LOGIN_FAIL, 0), -- login_failures
            U.LAST_LOGIN_MS, -- last_login_ms
            null, -- reactivated_ms
            false, -- force_password_change
            false, -- never_expires
            U.STAT = 0, -- enabled
            U.STAT = 3, -- inactive
            U.STAT = 2, -- locked
            false -- processing_account
        FROM OLD_USR U
        WHERE NOT EXISTS (
            SELECT NULL
            FROM account a
            WHERE a.user_id = U.NAME) -- only copy users not already present
        AND U.GRP + 0 = 0 -- conversion of bit value to integer. Ignore groups
        ORDER BY id;

        -- Work out what to set our auto_increment start value to
        SELECT
            CONCAT(
            'ALTER TABLE account AUTO_INCREMENT = ',
            COALESCE(MAX(id) + 1, 1))
        INTO @alter_table_sql
        FROM account;

        PREPARE alter_table_stmt FROM @alter_table_sql;
        EXECUTE alter_table_stmt;
    END IF;
END//
DELIMITER ;
CALL legacy_migrate_usr();
DROP PROCEDURE legacy_migrate_usr;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
