-- stop note level warnings about objects (not)? existing
SET @old_sql_notes=@@sql_notes, sql_notes=0;

-- This is here to stop the following warnings

-- WARN ..... DB: Unknown table 'stroom.BAT_SRCH' (SQL State: 42S02 - Error Code: 1051)
-- WARN ..... DB: Unknown table 'stroom.STROOM_VER_UPGRADE' (SQL State: 42S02 - Error Code: 1051)
-- WARN ..... DB: Unknown table 'stroom.STROOM_VER' (SQL State: 42S02 - Error Code: 1051)

-- We can't change the V5_0_0_0_* script so we have to add this script in before it.
