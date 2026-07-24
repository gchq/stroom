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

-- Drop the unused oauth_client.uri_pattern column. redirect_uri validation is an exact match against
-- the application's public root and does not use this column.

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

DROP PROCEDURE IF EXISTS V07_13_00_015__drop_oauth_client_uri_pattern;

DELIMITER $$

CREATE PROCEDURE V07_13_00_015__drop_oauth_client_uri_pattern ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(1)
    INTO object_count
    FROM information_schema.columns
    WHERE table_schema = database()
    AND table_name = 'oauth_client'
    AND column_name = 'uri_pattern';

    IF object_count = 1 THEN
        ALTER TABLE oauth_client DROP COLUMN uri_pattern;
    END IF;
END $$

DELIMITER ;

CALL V07_13_00_015__drop_oauth_client_uri_pattern;

DROP PROCEDURE IF EXISTS V07_13_00_015__drop_oauth_client_uri_pattern;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
