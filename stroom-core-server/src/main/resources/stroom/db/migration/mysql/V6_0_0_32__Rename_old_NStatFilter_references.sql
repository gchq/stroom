/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- The pipeline element type 'StatisticsFilter' used to be called 'NStatFilter'
-- This was change a long time ago but some instances in database may not have been
-- migrated.
update PIPE
set DAT = REPLACE(
    DAT,
    '<type>NStatFilter</type>',
    '<type>StatisticsFilter</type>')
WHERE DAT like '%<type>NStatFilter</type>%';