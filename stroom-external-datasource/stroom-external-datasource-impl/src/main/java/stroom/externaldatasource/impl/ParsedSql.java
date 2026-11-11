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

package stroom.externaldatasource.impl;

import java.util.List;

/// The SQL of a query, read by [SqlStatementParser].
///
/// @param sql          The SQL as the user wrote it.
/// @param jdbcSql      The SQL with each placeholder replaced by `?`, ready to prepare.
/// @param placeholders The placeholders in the order their `?` appear.
public record ParsedSql(String sql, String jdbcSql, List<SqlPlaceholder> placeholders) {

    /// Copies the placeholder list, so the record cannot change.
    public ParsedSql {
        placeholders = List.copyOf(placeholders);
    }

    /// @return True if the SQL takes the query's time range through `:from` or `:to`.
    public boolean usesTimeRange() {
        return placeholders.stream().anyMatch(SqlPlaceholder::isTimeRange);
    }
}
