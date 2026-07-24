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

-- Supports self service account unlocking for the internal identity provider, see GH-5656.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_13_00_010__account_self_service_unlock;

DELIMITER $$

CREATE PROCEDURE V07_13_00_010__account_self_service_unlock ()
BEGIN
    DECLARE object_count integer;
    DECLARE duplicate_count integer;
    DECLARE duplicate_emails text;

    -- The SHA-256 hash of the secret in the most recently issued password reset link for the account.
    -- The link is an opaque random string; only its hash is held here, so the link cannot be recovered
    -- from the database. Issuing a new link replaces this, so an earlier one stops working, and it is
    -- cleared whenever the password is set, so a link cannot be used twice or survive a password change.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'account'
    AND column_name = 'reset_token_hash';

    IF object_count = 0 THEN
        ALTER TABLE account ADD COLUMN reset_token_hash varchar(64) DEFAULT NULL;
    END IF;

    -- When the current password reset link expires, as epoch millis. Held explicitly rather than derived
    -- so that changing the configured link lifetime does not retroactively change links already issued.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'account'
    AND column_name = 'reset_token_expiry_ms';

    IF object_count = 0 THEN
        ALTER TABLE account ADD COLUMN reset_token_expiry_ms bigint DEFAULT NULL;
    END IF;

    -- When a password reset email was last requested for this account, used to stop the unauthenticated
    -- reset endpoint being used to send mail to someone's inbox over and over. Held against the account
    -- rather than in a table of its own so that it is bounded by the number of accounts, and against the
    -- database rather than in memory so that the limit holds across a cluster.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'account'
    AND column_name = 'reset_email_requested_ms';

    IF object_count = 0 THEN
        ALTER TABLE account ADD COLUMN reset_email_requested_ms bigint DEFAULT NULL;
    END IF;

    -- 'Forgot password' finds the account to reset by its email address, so an address must identify at
    -- most one account. An account may still have no email address at all, in which case it simply
    -- cannot be reset by email; a UNIQUE index permits any number of NULLs.
    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.statistics
    WHERE table_schema = database()
    AND table_name = 'account'
    AND index_name = 'account_email_idx';

    IF object_count = 0 THEN
        -- Adding the index would fail with a bare duplicate key error, naming only the first clash.
        -- Check first so that the operator is told exactly which addresses to fix.
        SELECT COUNT(1), GROUP_CONCAT(email SEPARATOR ', ')
        INTO duplicate_count, duplicate_emails
        FROM (
            SELECT email
            FROM account
            WHERE email IS NOT NULL
            GROUP BY email
            HAVING COUNT(1) > 1
        ) duplicates;

        IF duplicate_count > 0 THEN
            SET @message_text = CONCAT(
                'Cannot make account.email unique because these email addresses are used by more than ',
                'one account: ', duplicate_emails, '. Give each account a unique email address, or ',
                'clear the email address of all but one, then start stroom again.');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message_text;
        END IF;

        ALTER TABLE account ADD UNIQUE KEY account_email_idx (email);
    END IF;
END $$

DELIMITER ;

CALL V07_13_00_010__account_self_service_unlock;

DROP PROCEDURE IF EXISTS V07_13_00_010__account_self_service_unlock;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
