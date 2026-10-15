-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
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

-- Chats saved before read tracking (7.13) have never been marked read, so would all show as unread after the
-- upgrade. Mark everything in them as read. When this runs on an upgrade, only those chats have no
-- last_read_message_id.
UPDATE ai_chat c
SET c.last_read_message_id = (
    SELECT MAX(m.id)
    FROM ai_chat_message m
    WHERE m.fk_ai_chat_id = c.id)
WHERE c.last_read_message_id IS NULL;

SET SQL_NOTES=@OLD_SQL_NOTES;
