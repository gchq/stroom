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

DROP PROCEDURE IF EXISTS V07_02_00_110;
DROP PROCEDURE IF EXISTS security_run_sql_v1;

DELIMITER $$

-- DO NOT change this without reading the header!
CREATE PROCEDURE security_run_sql_v1 (
    p_sql_stmt varchar(1000)
)
BEGIN

    SET @sqlstmt = p_sql_stmt;

    SELECT CONCAT('Running sql: ', @sqlstmt);

    PREPARE stmt FROM @sqlstmt;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END $$

CREATE PROCEDURE V07_02_00_110 ()
BEGIN
    DECLARE object_count integer;

    select count(*)
    INTO object_count
    from information_schema.tables
    where table_name in ('token', 'token_type', 'stroom_user')
    and table_schema = database();

    -- If the tables are not there then we don't need to worry as there is nothing to migrate
    IF object_count = 3 THEN
        -- This is frankly a bit grim. Because we are migrating between modules we have to
        -- use dynamic sql as the tables may not be there. Also, if anyone is running the different
        -- db modules in separate dbs then there is nothing we can do.
        CALL security_run_sql_v1(CONCAT(
            ' INSERT INTO api_key (',
            '     version,',
            '     create_time_ms,',
            '     create_user,',
            '     update_time_ms,',
            '     update_user,',
            '     fk_owner_uuid,',
            '     api_key_hash,',
            '     api_key_prefix,',
            '     expires_on_ms,',
            '     name,',
            '     comments,',
            '     enabled)',
            ' select',
            '     1,',
            '     t.create_time_ms,',
            '     t.create_user,',
            '     t.update_time_ms,',
            '     t.update_user,',
            '     s.uuid,',
            '     CONCAT(''LEGACY_API_KEY'', ''_'', t.id),', -- Add id to make it unique
            '     CONCAT(''LEGACY_API_KEY'', ''_'', t.id),', -- Add id to make it unique
            '     t.expires_on_ms,',
            '     CONCAT(''LEGACY_API_KEY'', ''_'', t.id),', -- Add id to make it unique
            '     t.data,', -- put the api key in the comments, as you used to be able to see the full key
            '     t.enabled',
            ' from token t',
            ' inner join token_type tt on tt.id = t.fk_token_type_id',
            ' inner join account a on a.id = t.fk_account_id',
            ' inner join stroom_user s on s.name = a.user_id',
            ' where tt.type = ''api''',
            ' and t.enabled = true;'));  -- No point migrating disabled ones

        -- Now delete the ones we migrated
        CALL security_run_sql_v1(CONCAT(
            ' delete t ',
            ' from token t',
            ' inner join token_type tt on tt.id = t.fk_token_type_id',
            ' where tt.type = ''api''',
            ' and t.enabled = true;'));
    END IF;
END $$

DELIMITER ;

CALL V07_02_00_110;

DROP PROCEDURE IF EXISTS V07_02_00_110;
DROP PROCEDURE IF EXISTS security_run_sql_v1;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
