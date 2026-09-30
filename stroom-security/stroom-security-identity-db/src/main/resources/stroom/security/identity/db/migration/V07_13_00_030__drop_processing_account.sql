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

-- Removes account.processing_account, which nothing sets and which grants nothing.
--
-- The flag marked an account as a non-interactive identity: hidden from the account list and search,
-- and refused both interactive sign in and password reset. No code path ever set it. Every migration
-- that populates the table writes false, and the create request carries no field for it, so the only
-- writer was an account update accepting the whole account object from the caller - which no longer
-- happens now that an update carries only the fields being changed.
--
-- It is also not how either machine identity in Stroom works. The internal processing user holds a
-- short lived self issued token and has no account row at all, and a data sender authenticating with
-- a client certificate is identified by the certificate's common name, deliberately without an account.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_13_00_030__drop_processing_account;

DELIMITER $$

CREATE PROCEDURE V07_13_00_030__drop_processing_account ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'account'
    AND column_name = 'processing_account';

    IF object_count = 1 THEN
        -- Fail closed before the column goes. Such an account is currently barred from signing in, so
        -- dropping the column without this would silently turn it into an ordinary, usable account.
        -- Disabling is the control that expresses "barred" now, and unlike the flag an administrator
        -- can see it. This is expected to match no rows on any database that has not been hand edited.
        UPDATE account
        SET enabled = 0
        WHERE processing_account = 1;

        ALTER TABLE account DROP COLUMN processing_account;
    END IF;
END $$

DELIMITER ;

CALL V07_13_00_030__drop_processing_account;

DROP PROCEDURE IF EXISTS V07_13_00_030__drop_processing_account;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
