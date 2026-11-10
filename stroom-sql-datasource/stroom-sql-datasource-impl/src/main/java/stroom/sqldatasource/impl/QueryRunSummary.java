/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqldatasource.impl;

import java.util.List;

/// What running the SQL of a query read.
///
/// @param columns         The columns the SQL returned.
/// @param unmatchedFields The query's fields that no column supplied.
/// @param rowsRead        The rows read from the database.
/// @param rowsPassed      The rows that passed the `where` expression.
/// @param limitReached    True if the SQL returned more rows than the limit, so some were not read.
/// @param maxRows         The row limit.
/// @param info            Something to tell the user about the run, such as the data Amazon Athena
///                        scanned, or `null`.
public record QueryRunSummary(List<SqlColumn> columns,
                              List<String> unmatchedFields,
                              long rowsRead,
                              long rowsPassed,
                              boolean limitReached,
                              int maxRows,
                              String info) {

}
