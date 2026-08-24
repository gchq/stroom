-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
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

-- Reduces the account lockout to the one thing it is for: blunting repeated wrong passwords.
--
--   locked          )
--   locked_until_ms ) -> failure_locked_ms
--   login_failures    -> failure_count
--
-- Three changes, in one migration because they are one idea.
--
-- Naming. Locking is not an administrative act. An administrator prevents an account being used by
-- disabling it, and only ever unlocks - and then only when the lock will not clear itself and self service
-- unlock is unavailable. Naming these columns as a group makes it harder to read them as general state.
--
-- Meaning. The old column held when the lock was due to end, worked out when the lock was applied, so it
-- was the configured duration already spent. Changing that duration then had no effect on any lock already
-- in force. Holding the moment the lock was applied and adding the configured duration at the point of
-- asking makes the setting mean what an administrator expects, for existing locks as well as new ones. A
-- duration of zero now means the lock does not lapse - including for locks already held, so setting it
-- during an incident makes every one of them permanent until an administrator releases it.
--
-- Shape. Once the column holds when the lock was applied, the boolean says nothing the timestamp does not:
-- a lock exists exactly when there is a time it was applied. Two columns that must agree are two columns
-- that can disagree, and a writer that clears one without the other leaves a lock that no longer means what
-- it says. One column cannot disagree with itself.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_13_00_035__account_lock_state;

DELIMITER $$

CREATE PROCEDURE V07_13_00_035__account_lock_state ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'account'
    AND column_name = 'locked';

    IF object_count = 1 THEN

        -- Carry over locks an administrator applied by hand, while `locked` still means "locked for any
        -- reason". A lock with no end time is the best available reading of that: a failure lock normally
        -- carries one. Disabling is the control that expresses an administrator barring an account now, and
        -- unlike a permanent lock it is visible for what it is.
        --
        -- This cannot tell a hand applied lock from a failure lock in a deployment configured with a zero
        -- lock duration, where failure locks are also stored with no end time. Those accounts become
        -- disabled. That fails closed - access stays barred until an administrator acts - but it is visible,
        -- and such deployments should expect to re-enable deliberately after upgrading.
        UPDATE account
        SET enabled = 0,
            locked = 0
        WHERE locked = 1
        AND locked_until_ms IS NULL;

        ALTER TABLE account
            CHANGE COLUMN locked_until_ms failure_locked_ms bigint DEFAULT NULL,
            CHANGE COLUMN login_failures failure_count int NOT NULL DEFAULT '0';

        -- When each surviving lock was applied cannot be recovered: the old column held only when it was
        -- due to end, and the duration that produced it is configuration rather than data. Treat them as
        -- locked now, so each serves at most one further full duration. That errs towards keeping an
        -- account locked rather than releasing one early, and it is bounded.
        UPDATE account
        SET failure_locked_ms = UNIX_TIMESTAMP() * 1000
        WHERE locked = 1;

        -- No lock means no lock time. Any end time left on an unlocked account is stale and must go, or the
        -- single column would report a lock that is not there.
        UPDATE account
        SET failure_locked_ms = NULL
        WHERE locked = 0;

        ALTER TABLE account
            DROP COLUMN locked;
    END IF;
END $$

DELIMITER ;

CALL V07_13_00_035__account_lock_state;

DROP PROCEDURE IF EXISTS V07_13_00_035__account_lock_state;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
