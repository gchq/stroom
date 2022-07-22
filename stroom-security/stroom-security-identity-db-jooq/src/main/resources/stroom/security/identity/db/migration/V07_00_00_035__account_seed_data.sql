-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

DELIMITER $$

DROP PROCEDURE IF EXISTS identity_insert_admin_account_if_absent $$

CREATE PROCEDURE identity_insert_admin_account_if_absent ()
BEGIN
    DECLARE object_count integer;

    SELECT COUNT(*)
    INTO object_count
    FROM account
    WHERE user_id = "admin";

    IF object_count = 0 THEN
        INSERT INTO account (
          version,
          create_time_ms,
          create_user,
          update_time_ms,
          update_user,
          user_id,
          password_hash,
          password_last_changed_ms,
          force_password_change,
          never_expires,
          enabled,
          inactive,
          locked,
          processing_account)
        VALUES (
            1,
            UNIX_TIMESTAMP() * 1000,
            "Flyway migration",
            UNIX_TIMESTAMP() * 1000,
            "Flyway migration",
            "admin",
            "$2a$10$THzPVeDX70fBaFPjZoY1fOXnCCAezhhYV/LO09w.3JKIybPgRMSiW",
            UNIX_TIMESTAMP() * 1000,
            true,
            true,
            true,
            false,
            false,
            false);
    END IF;
END $$

DELIMITER ;

-- idempotent
CALL identity_insert_admin_account_if_absent();

DROP PROCEDURE IF EXISTS identity_insert_admin_account_if_absent;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
