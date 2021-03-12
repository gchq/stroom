-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

DELIMITER $$

DROP PROCEDURE IF EXISTS identity_insert_json_web_key_if_absent $$

CREATE PROCEDURE identity_insert_json_web_key_if_absent ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(*)
    INTO object_count
    FROM json_web_key
    WHERE key_id = '1ec7a983-317d-46ce-ae93-ce42bc217e52';

    IF object_count = 0 THEN
        -- This is the public/private key pair first used by Stroom Auth.
        -- It's akin to a default password and must be reset.
        INSERT INTO json_web_key (
            version,
            create_time_ms,
            create_user,
            update_time_ms,
            update_user,
            fk_token_type_id,
            key_id,
            json,
            enabled)
        VALUES (
            1,
            UNIX_TIMESTAMP() * 1000,
            "admin",
            UNIX_TIMESTAMP() * 1000,
            "admin",
            (
                SELECT tt.id
                FROM token_type tt
                WHERE tt.type = "user"),
            '1ec7a983-317d-46ce-ae93-ce42bc217e52',
            '{"kty":"RSA","kid":"1ec7a983-317d-46ce-ae93-ce42bc217e52","n":"72G4eRyG91OlsKOs-s2vBsbvXV5IKDOUpqDkFKYR_pCJwSBgD4BZYZ_qnIXx5L6cIK1Hsk_nMchHnL9lK7-nIt_bO41AyBes2IF2TTPuPKwqGY-aKCcuRh_BkDrCPZBR7-iyuYigL8MwgiP-yX6ieNpXzrfy3C8IYwLrKjLt39fb0fBod6_nrBPZeBUlH5m5pswNDfbOJII3bEGq5uQw5SZbaGBEEVbvkLNTROByK1PuOezfrpAyTWHCsG-zEsQ0HuIFyVo07sFJhV1AypPL-s71vKi6sDY46Mc5I2KV4CHbdvIhHT4xTaAhE9VimZgQW6sVvpRalV5XL8czjWVjtw","e":"AQAB","d":"HnwZXAMQBQs3_Ii7jK0I7xoCfad2FPiMo7O1mBOWEw8hG-EdmpvDxjTxUcGVDoZfp6GpkcGvNZ3F0OZm4e1kQYK0jp7scw7gyimigS5t1nguXFb3UMm8kN2WbuGsvt5UMPM3X31QuQRodwpSdiKUWkOkDwVJ_lRXAxTqEdOui2TY2WzKijvBEahyA2pqUGvrajF2GKFbZzXO1zagq6VhGCQohvCkaRWB-tVzc4-Ns6v3jqCKRpYYPrxD4C0RZ1K6InkxtXg1qYrcGMAPzCbqdjLoN41k-ZLNrp8rUSbRf2JAv9A8Ca2r6GDa0PcsySIbUWfduXOgGSuSfW6Hze1nMQ","p":"__6DyUpzhXxh6JfRghv2K371tCeV4l1Q45S92Ea1IX3Y6iBweK4W6ToQYTM7l_0bA5eaJ3kywvmpFqzpEcmtsOJOyg_rPQe3GeBDhUJLHqEPDT4t2yc8eQnrFOdWLEYg2nYWLoOkFPS36UBgJtaxIpyj16qidk-DYSEXpeDcqkM","q":"72McA2LIVj3hjlt_JsLpMFpcMm7E6knS-YITQUlwIFmVcW5tNQkC871o5x5AJL2Zj7MwxGHEx1WLzwd8B6lGkFP4BTpjCEoo_kXS-6IWyI3p6v7EmIb1O5Av8W37CA3VkPtuM-_dTFGlEW2vF0_ZetvTL49FlRx01Ekt48pvK30","dp":"xEAYQ_6hpVn_rVKGORq6lAnWz2_xhgJH-tCS4fUC81QJMSQBVWMRCWeMGxgtvY06Ynycn1pYwgSnzkxsuUhFse8su9eMXdNGWb4FxWlXMXoDkgFzIiloQNqLsBDRjUuN8CzLQImHBtG9FEJX9C5uybwQF0wnFFBMxe-as345bQU","dq":"1z03vNue4dw16Dfgdcueu7kjWL08FKRYK7uG8JbFWHDz68-sJZl6rAlMPzJ13hMT9Z7aZFi8A7apRHaoUIMlTTQStzCuRo_Xl_jUISi2b5EaGA8GWVZPPUUBtoR6x90Yf4lypwQu6CYo0yjZ244SL2Nj2Ulq-Q1jBlTeDAjCOEk","qi":"a6D1TYa8dgJDqKfZ926SqPxzA3FiJPWAfo8BTyIahZFqqaPAaXEfKUTTCZKmIqsWwNntGcdmDhnNGZ11E2X8MPYkRpnIQj3BywtDzRbPGJ8AFcvy6Zq_vYl874LFrkvGPILe6NxabRCyrFgDKOQ-gIjkL83t0wyS_vVlrGLtCrk"}',
            true);
    END IF;
END $$

DELIMITER ;

-- idempotent
CALL identity_insert_json_web_key_if_absent();

DROP PROCEDURE IF EXISTS identity_insert_json_web_key_if_absent;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
