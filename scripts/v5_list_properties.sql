-- To be run when migrating from v5 to v7
-- 
-- Run with the mysql --table arg to get formatted output
-- e.g.
-- mysql --force --table -h"localhost" -P"3307" -u"stroomuser" -p"stroompassword1" stroom < v5_list_properties.sql > v5_list_properties.out 2>&1

\! echo 'List non-default Stroom properties in case they need to be manually migrated later.';

SELECT
  NAME,
  VAL
FROM _GLOB_PROP;

\! echo 'Finished';
