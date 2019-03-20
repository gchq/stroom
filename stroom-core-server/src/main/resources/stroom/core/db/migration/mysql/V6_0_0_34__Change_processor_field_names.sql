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
    '<field>streamTypeName</field>',
    '<field>Type</field>')
WHERE DAT like '%<field>streamTypeName</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>pipelineUuid</field>',
    '<field>Pipeline UUID</field>')
WHERE DAT like '%<field>pipelineUuid</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>feedName</field>',
    '<field>Feed</field>')
WHERE DAT like '%<field>feedName</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>Stream Id</field>',
    '<field>Id</field>')
WHERE DAT like '%<field>Stream Id</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>Parent Stream Id</field>',
    '<field>Parent Id</field>')
WHERE DAT like '%<field>Parent Stream Id</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>FileSize</field>',
    '<field>File Size</field>')
WHERE DAT like '%<field>FileSize</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>StreamSize</field>',
    '<field>Raw Size</field>')
WHERE DAT like '%<field>StreamSize</field>%';

update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>RecRead</field>',
    '<field>Read Count</field>')
WHERE DAT like '%<field>RecRead</field>%';


update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>RecWrite</field>',
    '<field>Write Count</field>')
WHERE DAT like '%<field>RecWrite</field>%';


update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>RecInfo</field>',
    '<field>Info Count</field>')
WHERE DAT like '%<field>RecInfo</field>%';


update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>RecWarn</field>',
    '<field>Warning Count</field>')
WHERE DAT like '%<field>RecWarn</field>%';


update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>RecError</field>',
    '<field>Error Count</field>')
WHERE DAT like '%<field>RecError</field>%';


update STRM_PROC_FILT
set DAT = REPLACE(
    DAT,
    '<field>RecFatal</field>',
    '<field>Fatal Error Count</field>')
WHERE DAT like '%<field>RecFatal</field>%';
