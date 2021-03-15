-- Script to drop legacy databases that are not used
-- in v7. MUST be run as root, or with drop database
-- privileges. MUST only be run after the auth database
-- has been backed up and copied into the stroom database.

-- Run it like 
-- docker exec -i stroom-all-dbs mysql --force -u"root" -p"my-secret-pw" < v7_drop_unused_databases.sql > v7_drop_unused_databases.out 2>&1

-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;


\! echo 'Dropping database annotations';
DROP DATABASE IF EXISTS annotations;

\! echo 'Dropping database auth';
DROP DATABASE IF EXISTS auth;

\! echo 'Dropping database config';
DROP DATABASE IF EXISTS config;

\! echo 'Dropping database datameta';
DROP DATABASE IF EXISTS datameta;

\! echo 'Dropping database explorer';
DROP DATABASE IF EXISTS explorer;

\! echo 'Dropping database process';
DROP DATABASE IF EXISTS process;



\! echo 'Dropping user annotationsuser';
DROP USER 'annotationsuser'@'%';

\! echo 'Dropping user authuser';
DROP USER 'authuser'@'%';

\! echo 'Dropping user configuser';
DROP USER 'configuser'@'%';

\! echo 'Dropping user datametauser';
DROP USER 'datametauser'@'%';

\! echo 'Dropping user exploreruser';
DROP USER 'exploreruser'@'%';

\! echo 'Dropping user processuser';
DROP USER 'processuser'@'%';


SET SQL_NOTES=@OLD_SQL_NOTES;
