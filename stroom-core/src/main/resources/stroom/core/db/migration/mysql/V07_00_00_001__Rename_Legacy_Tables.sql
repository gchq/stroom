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

-- stop note level warnings about objects (not)? existing
set @old_sql_notes=@@sql_notes, sql_notes=0;

-- Rename all the legacy tables to 'OLD_.....'
-- All calls are idempotent
CALL core_rename_legacy_table_if_exists_v1('ACTIVITY');
CALL core_rename_legacy_table_if_exists_v1('APP_PERM');
CALL core_rename_legacy_table_if_exists_v1('CLSTR_LK');
CALL core_rename_legacy_table_if_exists_v1('DASH');
CALL core_rename_legacy_table_if_exists_v1('DICT');
CALL core_rename_legacy_table_if_exists_v1('DOC_PERM');
CALL core_rename_legacy_table_if_exists_v1('FD');
CALL core_rename_legacy_table_if_exists_v1('GLOB_PROP');
CALL core_rename_legacy_table_if_exists_v1('IDX');
CALL core_rename_legacy_table_if_exists_v1('IDX_SHRD');
CALL core_rename_legacy_table_if_exists_v1('IDX_VOL');
CALL core_rename_legacy_table_if_exists_v1('JB');
CALL core_rename_legacy_table_if_exists_v1('JB_ND');
CALL core_rename_legacy_table_if_exists_v1('ND');
CALL core_rename_legacy_table_if_exists_v1('PERM');
CALL core_rename_legacy_table_if_exists_v1('PIPE');
CALL core_rename_legacy_table_if_exists_v1('POLICY');
CALL core_rename_legacy_table_if_exists_v1('QUERY');
CALL core_rename_legacy_table_if_exists_v1('RK');
CALL core_rename_legacy_table_if_exists_v1('SCRIPT');
CALL core_rename_legacy_table_if_exists_v1('STATE_DAT_SRC');
CALL core_rename_legacy_table_if_exists_v1('STAT_DAT_SRC');
CALL core_rename_legacy_table_if_exists_v1('STRM');
CALL core_rename_legacy_table_if_exists_v1('STRM_ATR_KEY');
CALL core_rename_legacy_table_if_exists_v1('STRM_ATR_VAL');
CALL core_rename_legacy_table_if_exists_v1('STRM_PROC');
CALL core_rename_legacy_table_if_exists_v1('STRM_PROC_FILT');
CALL core_rename_legacy_table_if_exists_v1('STRM_PROC_FILT_TRAC');
CALL core_rename_legacy_table_if_exists_v1('STRM_TASK');
CALL core_rename_legacy_table_if_exists_v1('STRM_TP');
CALL core_rename_legacy_table_if_exists_v1('STRM_VOL');
CALL core_rename_legacy_table_if_exists_v1('STROOM_STATS_STORE');
CALL core_rename_legacy_table_if_exists_v1('TXT_CONV');
CALL core_rename_legacy_table_if_exists_v1('USR');
CALL core_rename_legacy_table_if_exists_v1('USR_GRP_USR');
CALL core_rename_legacy_table_if_exists_v1('VIS');
CALL core_rename_legacy_table_if_exists_v1('VOL');
CALL core_rename_legacy_table_if_exists_v1('VOL_STATE');
CALL core_rename_legacy_table_if_exists_v1('XML_SCHEMA');
CALL core_rename_legacy_table_if_exists_v1('XSLT');
CALL core_rename_legacy_table_if_exists_v1('explorerTreeNode');
CALL core_rename_legacy_table_if_exists_v1('explorerTreePath');

-- Reset to the original value
SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
