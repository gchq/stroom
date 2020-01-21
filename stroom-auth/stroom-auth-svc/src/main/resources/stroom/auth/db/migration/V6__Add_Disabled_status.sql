-- There is a new status, 'disabled'. Unfortunately there is already a 'disabled' status.
-- The current 'disabled' status is used to mean 'inactive', so it needs to be renamed.
-- disabled => inactive
UPDATE users SET state = 'inactive' WHERE state = 'disabled';