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

import stroom.sqldatasource.shared.SqlDataSourceDoc;

import java.util.Objects;

/// The limits a query runs under: the document's own, reduced to the administrator's ceilings.
///
/// @param timeoutSeconds The query timeout in seconds, at least 1.
/// @param maxRows        The most rows the SQL may return, at least 1.
public record SqlQueryLimits(int timeoutSeconds, int maxRows) {

    /// Works out the limits for a document.
    ///
    /// @param doc    The SQL Data Source.
    /// @param config The administrator's ceilings.
    /// @return The limits.
    public static SqlQueryLimits of(final SqlDataSourceDoc doc, final SqlDataSourceConfig config) {
        final long timeoutCeiling = Math.max(1, config.getMaxQueryTimeout().toSeconds());
        final int timeout = Objects.requireNonNullElse(
                doc.getQueryTimeoutSeconds(),
                SqlDataSourceDoc.DEFAULT_QUERY_TIMEOUT_SECONDS);
        final int maxRows = Objects.requireNonNullElse(doc.getMaxRows(), SqlDataSourceDoc.DEFAULT_MAX_ROWS);
        return new SqlQueryLimits(
                (int) Math.clamp(timeout, 1, Math.min(timeoutCeiling, Integer.MAX_VALUE)),
                Math.clamp(maxRows, 1, Math.max(1, config.getMaxRowsLimit())));
    }
}
