/*
 * Copyright 2016-2026 Crown Copyright
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

-- Five scripts were accidentally named as v7.14 on the v7.13 branch.
-- The scripts themselves have been renamed to v7.13.
-- This script corrects the names in the schema history to prevent migration failures.

UPDATE cross_module_schema_history
SET
    version = REPLACE(version, '07.14', '07.13'),
    script = REPLACE(script, '07_14', '07_13')
WHERE script = 'stroom.app.db.migration.V07_14_00_005__populate_doc_dependency_processor_filters';

UPDATE docstore_schema_history
SET
    version = REPLACE(version, '07.14', '07.13'),
    script = REPLACE(script, '07_14', '07_13')
WHERE script IN (
    'V07_14_00_001__split_doc_table.sql',
    'V07_14_00_002__add_version_column.sql',
    'V07_14_00_003__doc_dependency.sql',
    'stroom.docstore.impl.db.migration.V07_14_00_004__populate_doc_dependency');
