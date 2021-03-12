-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

DELIMITER $$

DROP PROCEDURE IF EXISTS identity_insert_type_if_absent $$

CREATE PROCEDURE identity_insert_type_if_absent (
    p_type_name varchar(1000)
)
BEGIN
    SELECT COUNT(*)
    INTO object_count
    FROM token_type
    WHERE type = p_type_name;

    IF object_count = 0 THEN
        INSERT INTO token_type (
            type)
        VALUES (
            p_type_name);
    END IF;
END $$

DELIMITER ;

-- idempotent
CALL identity_insert_type_if_absent("user");
CALL identity_insert_type_if_absent("api");
CALL identity_insert_type_if_absent("email_reset");

DROP PROCEDURE IF EXISTS identity_insert_type_if_absent $$

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
