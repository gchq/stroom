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

-- We want to delete the admin account that was added by V07_00_00_035__account_seed_data.sql
-- but ONLY when we are doing a fresh install of stroom, hence the predicate on create_time_ms.
-- I.e. flyway is running all the migrations in one go, or all >=7.0 migrations in one go.
-- We can't risk deleting the admin account during the migration of an existing system in case it
-- is in use, so only delete if it was created < 10mins ago.
-- The admin account will now get conditionally created by stroom.app.AdminAccountBootstrap depending on
-- config.
DELETE FROM account
WHERE user_id = 'admin'
AND create_user = 'Flyway migration'
AND create_time_ms > ((UNIX_TIMESTAMP() - (10 * 60)) * 1000)
AND (login_count = 0 OR login_count IS NULL);

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
