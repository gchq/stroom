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

-- Set field names to be user friendly versions
update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>Pipeline UUID</field>',
    '<field>Pipeline</field>')
WHERE DAT like '%<field>Pipeline UUID</field>%';

