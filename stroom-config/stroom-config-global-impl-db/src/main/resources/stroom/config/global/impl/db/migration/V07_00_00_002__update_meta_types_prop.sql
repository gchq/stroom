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

-- metaTypes was changed from a String (with the value being a \n delimited list)
-- to a Set<String> which is serialised differently.
SET @PROP_NAME = 'stroom.data.meta.metaTypes';
SET @OLD_DELIMITER = '\n';

-- Change 'C\nA\nB' to '|A|B|C' (sorting the items)
-- A fair bit of credit to https://stackoverflow.com/a/32586238 for the row generation
-- and conversion of a delimited string to rows.
UPDATE config c2
CROSS JOIN (
  SELECT CONCAT(
      '|',
      GROUP_CONCAT(DISTINCT item ORDER BY item ASC SEPARATOR '|')) AS item_str
  FROM (
    SELECT SUBSTRING_INDEX(
        SUBSTRING_INDEX(c.val, @OLD_DELIMITER, sub0.aNum),
        @OLD_DELIMITER,
        -1) AS item
    FROM config c
    INNER JOIN
    (
        SELECT 1 + units.i + tens.i * 10 AS aNum, units.i + tens.i * 10 AS aSubscript
        FROM (
          SELECT 0 AS i
          UNION SELECT 1
          UNION SELECT 2
          UNION SELECT 3
          UNION SELECT 4
          UNION SELECT 5
          UNION SELECT 6
          UNION SELECT 7
          UNION SELECT 8
          UNION SELECT 9
        ) units
        CROSS JOIN (
          SELECT 0 AS i
          UNION SELECT 1
          UNION SELECT 2
          UNION SELECT 3
          UNION SELECT 4
          UNION SELECT 5
          UNION SELECT 6
          UNION SELECT 7
          UNION SELECT 8
          UNION SELECT 9
        ) tens
    ) sub0
    ON (1 + LENGTH(c.val) - LENGTH(REPLACE(c.val, @OLD_DELIMITER, ''))) >= sub0.aNum
    WHERE c.name = @PROP_NAME
    AND c.val LIKE CONCAT('%', @OLD_DELIMITER, '%')
    ORDER BY c.val
  ) items
) item_strs
SET c2.val = item_strs.item_str
WHERE c2.name = @PROP_NAME
AND c2.val LIKE CONCAT('%', @OLD_DELIMITER, '%');

SET SQL_NOTES=@OLD_SQL_NOTES;
