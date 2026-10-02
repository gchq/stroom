-- ------------------------------------------------------------------------
-- Copyright 2025 Crown Copyright
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

-- Supports time-limited (auto-expiring) account lockout for the internal identity provider.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_13_00_020__account_lockout_expiry;

DELIMITER $$

CREATE PROCEDURE V07_13_00_020__account_lockout_expiry ()
BEGIN
    DECLARE object_count integer;

    -- When a failure-driven lock auto-expires, as epoch millis. A lock set by repeated failed logins
    -- carries an expiry so that it clears itself after a configured period, rather than needing an
    -- administrator. NULL means the lock (if any) never expires: this is the case for a lock set
    -- manually by an administrator, and is the default for a freshly created account.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'account'
    AND column_name = 'locked_until_ms';

    IF object_count = 0 THEN
        ALTER TABLE account ADD COLUMN locked_until_ms bigint DEFAULT NULL;
    END IF;
END $$

DELIMITER ;

CALL V07_13_00_020__account_lockout_expiry;

DROP PROCEDURE IF EXISTS V07_13_00_020__account_lockout_expiry;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
