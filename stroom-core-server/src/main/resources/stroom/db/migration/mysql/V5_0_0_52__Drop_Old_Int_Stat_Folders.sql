-- Delete the child folders of '/Old Internal Statistics'

-- Additional sub-select needed to get round MySQL's inability to copy with having the table being
-- deleted from in an immediate sub-select
DELETE
FROM FOLDER
WHERE fk_folder_id IN (
    SELECT id
    FROM (
        SELECT *
        FROM FOLDER
        WHERE name = 'Old Internal Statistics'
        AND fk_folder_id IS NULL
    ) AS sub
);

-- Delete the folder '/Old Internal Statistics'
DELETE
FROM FOLDER
WHERE name = 'Old Internal Statistics'
AND fk_folder_id IS NULL;