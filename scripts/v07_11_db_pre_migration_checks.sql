-- A set of SQL queries to run before migrating from v6 to v7
-- See https://gchq.github.io/stroom-docs/releases/v07.11/upgrade-notes/
-- 
-- Run with the mysql --table arg to get formatted output
-- e.g.
-- mysql --force --table -h"localhost" -P"3306" -u"stroomuser" -p"stroompassword1" stroom < v07_11_db_pre_migration_checks.sql > v07_11_db_pre_migration_checks.out 2>&1
-- docker exec -i stroom-all-dbs mysql --force --table -h"localhost" -P"3307" -u"stroomuser" -p"stroompassword1" stroom < v07_11_db_pre_migration_checks.sql > v07_11_db_pre_migration_checks.out 2>&1


\! echo 'Find annotation entries with no assigned user. No action required if this returns nothing.';

SELECT a.id, a.title, ae.id AS entry_id
FROM annotation a
INNER JOIN annotation_entry ae ON a.id = ae.fk_annotation_id
WHERE ae.entry_user_uuid IS NULL OR ae.entry_user_uuid = '';

\! echo 'Listing all enabled Stroom users for reference';

SELECT uuid, name, display_name, full_name
FROM stroom_user su
WHERE is_group = false
AND enabled = true
ORDER by name;

\! echo 'Finished';
