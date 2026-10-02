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

package stroom.query.shared;

import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the table preferences a user has set in the UI, e.g. hidden columns, formats and sorts, to the
 * {@link ResultRequest result requests} derived from a StroomQL query.
 * <p>
 * StroomQL has no syntax for these presentation concerns so the columns produced by parsing a query always carry
 * default settings. The preferences are stored separately against the owning document and must be merged back in
 * before the query is executed, otherwise anything the user changed in the results table is ignored by the server.
 * </p>
 */
public final class QueryTablePreferencesUtil {

    private QueryTablePreferencesUtil() {
        // Utility class.
    }

    /**
     * Apply the supplied preferences to every result request in the supplied search request.
     *
     * @param searchRequest          The search request to modify.
     * @param queryTablePreferences  The preferences to apply, may be null in which case the request is returned as is.
     * @return A copy of the search request with the preferences applied.
     */
    public static SearchRequest applyTablePreferences(final SearchRequest searchRequest,
                                                      final QueryTablePreferences queryTablePreferences) {
        if (searchRequest == null || queryTablePreferences == null) {
            return searchRequest;
        }

        final List<ResultRequest> resultRequests = searchRequest.getResultRequests();
        if (NullSafe.isEmptyCollection(resultRequests)) {
            return searchRequest;
        }

        final List<ResultRequest> modifiedResultRequests = new ArrayList<>(resultRequests.size());
        for (final ResultRequest resultRequest : resultRequests) {
            modifiedResultRequests.add(applyTablePreferences(resultRequest, queryTablePreferences));
        }

        return searchRequest
                .copy()
                .resultRequests(modifiedResultRequests)
                .build();
    }

    /**
     * Apply the supplied preferences to the first table mapping of the supplied result request. Subsequent mappings
     * are left alone as the preferences only ever describe the table the user is looking at.
     *
     * @param resultRequest         The result request to modify.
     * @param queryTablePreferences The preferences to apply, may be null in which case the request is returned as is.
     * @return A copy of the result request with the preferences applied.
     */
    public static ResultRequest applyTablePreferences(final ResultRequest resultRequest,
                                                      final QueryTablePreferences queryTablePreferences) {
        if (resultRequest == null || queryTablePreferences == null) {
            return resultRequest;
        }

        final List<TableSettings> mappings = NullSafe.list(resultRequest.getMappings());
        if (mappings.isEmpty()) {
            return resultRequest;
        }

        final TableSettings tableSettings = mappings.get(0);
        final TableSettings.Builder builder = tableSettings.copy();
        builder.columns(applyColumnPreferences(
                NullSafe.list(tableSettings.getColumns()),
                queryTablePreferences.getColumns()));

        // Combine row filters.
        if (tableSettings.getAggregateFilter() == null) {
            builder.aggregateFilter(queryTablePreferences.getSelectionFilter());
        } else if (queryTablePreferences.getSelectionFilter() != null) {
            builder.aggregateFilter(ExpressionOperator
                    .builder()
                    .addOperators(tableSettings.getAggregateFilter(),
                            queryTablePreferences.getSelectionFilter())
                    .build());
        }

        builder.conditionalFormattingRules(queryTablePreferences.getConditionalFormattingRules());

        final List<TableSettings> modifiedMappings = new ArrayList<>(mappings.size());
        modifiedMappings.add(builder.build());
        modifiedMappings.addAll(mappings.subList(1, mappings.size()));

        return resultRequest
                .copy()
                .mappings(modifiedMappings)
                .build();
    }

    /**
     * Overlay the preferred settings onto the columns produced by parsing the query, matching on column id. Columns
     * with no matching preference are left untouched, and preferences that no longer match a column are ignored so
     * that stale preferences left behind by a query edit do nothing.
     */
    private static List<Column> applyColumnPreferences(final List<Column> columns,
                                                       final List<Column> preferredColumns) {
        final Map<String, Column> prefs = new HashMap<>();
        for (final Column preferredColumn : NullSafe.list(preferredColumns)) {
            // Deliberately tolerant of duplicate ids, last one wins.
            prefs.put(preferredColumn.getId(), preferredColumn);
        }

        final List<Column> modifiedColumns = new ArrayList<>(columns.size());
        for (final Column column : columns) {
            final Column.Builder columnBuilder = column.copy();
            final Column pref = prefs.get(column.getId());
            if (pref != null) {
                columnBuilder.filter(pref.getFilter());
                columnBuilder.columnFilter(pref.getColumnFilter());
                columnBuilder.columnValueSelection(pref.getColumnValueSelection());
                columnBuilder.width(pref.getWidth());
                columnBuilder.format(pref.getFormat());
                // Special columns are never shown to the user so the UI has no say over their visibility.
                if (!column.isSpecial()) {
                    columnBuilder.visible(pref.isVisible());
                }
                if (pref.getSort() != null) {
                    columnBuilder.sort(pref.getSort());
                }
            }
            modifiedColumns.add(columnBuilder.build());
        }
        return modifiedColumns;
    }
}
