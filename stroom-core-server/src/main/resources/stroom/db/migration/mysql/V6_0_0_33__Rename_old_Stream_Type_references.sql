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

-- Ensures any old processor filters have the stream type field updated to
-- its new type name
update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>Stream Type</field>',
    '<field>streamTypeName</field>')
WHERE DAT like '%<field>Stream Type</field>%';