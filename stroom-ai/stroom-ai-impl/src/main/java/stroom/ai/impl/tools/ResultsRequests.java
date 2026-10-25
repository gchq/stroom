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

package stroom.ai.impl.tools;

import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.QueryKey;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeRange;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// What makes the results of a table in the user's tab what they are: for a query, its StroomQL, parameters and time
/// range; for a dashboard table, its data source, expression, parameters and time range. A snapshot of the results
/// records a hash of these, so that it can be told stale when the tab's query has changed since. Things that don't
/// change which rows a search finds (e.g. the query key, the page shown, or column widths) are left out.
final class ResultsRequests {

    static final String QUERY = "the query";
    static final String DATA_SOURCE = "the data source";
    static final String EXPRESSION = "the expression";
    static final String PARAMETERS = "the parameters";
    static final String TIME_RANGE = "the time range";
    static final String SELECTION = "the selection filter";

    private ResultsRequests() {
        // Static methods only.
    }

    /// @param context A table's search request.
    /// @return A hash of what makes its results what they are, or null if there is no request.
    static String hash(final AskStroomAiContext context) {
        final Map<String, Object> aspects = aspects(context);
        if (aspects.isEmpty()) {
            return null;
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(JsonUtil.writeValueAsConsistentString(aspects, false)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// @param before A table's search request, as its results were taken from.
    /// @param now    The table's search request as the tab now stands.
    /// @return What has changed, e.g. "the query" and "the time range". Empty if nothing has, or either is null.
    static List<String> changes(final AskStroomAiContext before, final AskStroomAiContext now) {
        final Map<String, Object> was = aspects(before);
        final Map<String, Object> is = aspects(now);
        final List<String> changes = new ArrayList<>();
        if (was.isEmpty() || is.isEmpty()) {
            return changes;
        }
        for (final Map.Entry<String, Object> entry : is.entrySet()) {
            if (!Objects.equals(json(entry.getValue()), json(was.get(entry.getKey())))) {
                changes.add(entry.getKey());
            }
        }
        return changes;
    }

    /// @param context A table's search request.
    /// @return Its query key, or null if it has none (e.g. it hasn't been run).
    static QueryKey queryKey(final AskStroomAiContext context) {
        return switch (context) {
            case final QueryTableContext query -> NullSafe.get(query.getSearchRequest(),
                    QuerySearchRequest::getQueryKey);
            case final DashboardTableContext dashboard -> NullSafe.get(dashboard.getSearchRequest(),
                    DashboardSearchRequest::getQueryKey);
            case null, default -> null;
        };
    }

    /// @param context A table's search request.
    /// @return Its time range, for people, or null if it has none.
    static String timeRange(final AskStroomAiContext context) {
        final TimeRange timeRange = switch (context) {
            case final QueryTableContext query -> NullSafe.get(query.getSearchRequest(),
                    QuerySearchRequest::getQueryContext, QueryContext::getTimeRange);
            case final DashboardTableContext dashboard -> NullSafe.get(dashboard.getSearchRequest(),
                    DashboardSearchRequest::getSearch, Search::getTimeRange);
            case null, default -> null;
        };
        return NullSafe.get(timeRange, TimeRange::toString);
    }

    /// @param context A dashboard table's search request.
    /// @return The data source it searches, or null for a query (whose data source is named in its StroomQL).
    static DocRef dashboardDataSource(final AskStroomAiContext context) {
        return context instanceof final DashboardTableContext dashboard
                ? NullSafe.get(dashboard.getSearchRequest(), DashboardSearchRequest::getSearch,
                Search::getDataSourceRef)
                : null;
    }

    /// @param context A table's search request.
    /// @return Its query, for the audit log: the StroomQL, or the dashboard's expression.
    static String queryText(final AskStroomAiContext context) {
        return switch (context) {
            case final QueryTableContext query -> NullSafe.get(query.getSearchRequest(),
                    QuerySearchRequest::getQuery);
            case final DashboardTableContext dashboard -> NullSafe.get(dashboard.getSearchRequest(),
                    DashboardSearchRequest::getSearch, Search::getExpression, Object::toString);
            case null, default -> null;
        };
    }

    /// @param context A table's search request.
    /// @return True if it is a dashboard table with a column grouped, so each row may stand for many events.
    static boolean isGrouped(final AskStroomAiContext context) {
        return columns(context).stream().anyMatch(column -> column.getGroup() != null);
    }

    /// @param context A table's search request.
    /// @return True if it is a dashboard table whose columns include each row's stream and event ids (as the hidden
    /// columns a table keeps for them, or columns of its own), so its rows can be traced to their events.
    static boolean hasEventIdColumns(final AskStroomAiContext context) {
        final List<Column> columns = columns(context);
        return columns.stream().anyMatch(column -> SpecialColumns.RESERVED_STREAM_ID.equals(column.getId()))
               && columns.stream().anyMatch(column -> SpecialColumns.RESERVED_EVENT_ID.equals(column.getId()));
    }

    /// A dashboard table's search, changed to give one row per event with each event's stream and event ids: the
    /// same data source, expression, parameters, time range and column filters, with no column grouped and the
    /// table's hidden stream and event id columns added if it doesn't have them. It has no query key, so running it
    /// is a new search, separate from the dashboard's own.
    ///
    /// @param context A dashboard table's search request.
    /// @return The changed request.
    static DashboardTableContext ungroupedWithEventIds(final DashboardTableContext context) {
        final DashboardSearchRequest request = context.getSearchRequest();
        final List<ComponentResultRequest> tables = new ArrayList<>();
        for (final ComponentResultRequest component : NullSafe.list(request.getComponentResultRequests())) {
            if (component instanceof final TableResultRequest table && table.getTableSettings() != null) {
                final TableSettings settings = table.getTableSettings();
                final List<Column> columns = new ArrayList<>();
                for (final Column column : NullSafe.list(settings.getColumns())) {
                    columns.add(column.getGroup() != null
                            ? column.copy().group(null).build()
                            : column);
                }
                if (columns.stream().noneMatch(column -> SpecialColumns.RESERVED_STREAM_ID.equals(column.getId()))) {
                    columns.add(SpecialColumns.RESERVED_STREAM_ID_COLUMN);
                }
                if (columns.stream().noneMatch(column -> SpecialColumns.RESERVED_EVENT_ID.equals(column.getId()))) {
                    columns.add(SpecialColumns.RESERVED_EVENT_ID_COLUMN);
                }
                tables.add(table.copy()
                        .tableSettings(settings.copy().columns(columns).build())
                        .openGroups(null)
                        .groupSelection(null)
                        .build());
            } else {
                tables.add(component);
            }
        }
        return new DashboardTableContext(context.getDescription(), null, request.copy()
                .queryKey(null)
                .componentResultRequests(tables)
                .build());
    }

    /// @param context A table's search request.
    /// @return The type of each column's format (e.g. NUMBER, DATE_TIME), by column name, for a dashboard table;
    /// none for a query, whose columns' formats aren't in its request.
    static Map<String, String> columnFormats(final AskStroomAiContext context) {
        final Map<String, String> formats = new LinkedHashMap<>();
        for (final Column column : columns(context)) {
            if (column.getName() != null) {
                formats.put(column.getName(), column.getFormat() != null && column.getFormat().getType() != null
                        ? column.getFormat().getType().name()
                        : "GENERAL");
            }
        }
        return formats;
    }

    /// The expressions of a dashboard table's columns, by column name.
    ///
    /// @param context The table's search request.
    /// @return Each column's expression, by name; none for a query's table or a column without one.
    static Map<String, String> columnExpressions(final AskStroomAiContext context) {
        final Map<String, String> expressions = new LinkedHashMap<>();
        for (final Column column : columns(context)) {
            if (column.getName() != null && column.getExpression() != null) {
                expressions.put(column.getName(), column.getExpression());
            }
        }
        return expressions;
    }

    /// The group levels of a dashboard table's columns, by column name.
    ///
    /// @param context The table's search request.
    /// @return Each grouped column's level, from 0 for the top level, by name; none for a query's table.
    static Map<String, Integer> columnGroups(final AskStroomAiContext context) {
        final Map<String, Integer> groups = new LinkedHashMap<>();
        for (final Column column : columns(context)) {
            if (column.getName() != null && column.getGroup() != null) {
                groups.put(column.getName(), column.getGroup());
            }
        }
        return groups;
    }

    // The columns of a dashboard table's search request, or none for a query.
    private static List<Column> columns(final AskStroomAiContext context) {
        if (!(context instanceof final DashboardTableContext dashboard) || dashboard.getSearchRequest() == null) {
            return List.of();
        }
        return NullSafe.list(dashboard.getSearchRequest().getComponentResultRequests()).stream()
                .filter(TableResultRequest.class::isInstance)
                .map(TableResultRequest.class::cast)
                .map(TableResultRequest::getTableSettings)
                .filter(Objects::nonNull)
                .flatMap(settings -> NullSafe.list(settings.getColumns()).stream())
                .toList();
    }

    // What makes a request's results what they are, by what the user would call it, in order.
    private static Map<String, Object> aspects(final AskStroomAiContext context) {
        final Map<String, Object> aspects = new LinkedHashMap<>();
        if (context instanceof final QueryTableContext query && query.getSearchRequest() != null) {
            final QuerySearchRequest request = query.getSearchRequest();
            final QueryContext queryContext = request.getQueryContext();
            aspects.put(QUERY, NullSafe.string(request.getQuery()).strip());
            aspects.put(PARAMETERS, NullSafe.list(NullSafe.get(queryContext, QueryContext::getParams)));
            aspects.put(TIME_RANGE, NullSafe.get(queryContext, QueryContext::getTimeRange));
            aspects.put(SELECTION, NullSafe.get(queryContext, QueryContext::getAdditionalQueryExpression));
        } else if (context instanceof final DashboardTableContext dashboard
                   && dashboard.getSearchRequest() != null
                   && dashboard.getSearchRequest().getSearch() != null) {
            final Search search = dashboard.getSearchRequest().getSearch();
            aspects.put(DATA_SOURCE, NullSafe.get(search.getDataSourceRef(), DocRef::getUuid));
            aspects.put(EXPRESSION, search.getExpression());
            aspects.put(PARAMETERS, NullSafe.list(search.getParams()));
            aspects.put(TIME_RANGE, search.getTimeRange());
        }
        return aspects;
    }

    private static String json(final Object value) {
        return value == null
                ? null
                : JsonUtil.writeValueAsConsistentString(value, false);
    }
}
