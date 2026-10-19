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

package stroom.ai.impl;

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.impl.QueryService;
import stroom.query.language.SearchRequestFactory;
import stroom.query.shared.QueryDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/// Builds the settings of a new dashboard from a StroomQL query the agent wrote (see `propose_dashboard`): a query,
/// a table of the columns its `select` gives, and, if asked for, a data preview (a Text widget showing the event a
/// row comes from), laid out one above the other.
///
/// The query can be kept as StroomQL, in an Embedded Query (which shows its own table), or turned into a classic
/// dashboard: a Query with the expression its `where` gives, and a Table with its columns, grouping, sorting and
/// limit. A classic dashboard can't hold StroomQL's window, filter, having or show clauses, so a query with any of
/// them can only be kept as StroomQL.
public class DashboardBuilder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DashboardBuilder.class);

    /// Keeps the query as StroomQL, in an Embedded Query.
    public static final String STROOMQL = "stroomql";
    /// Turns the query into a classic Query (an expression) and Table.
    public static final String CLASSIC = "classic";
    /// The query types, as the model gives them.
    public static final List<String> QUERY_TYPES = List.of(STROOMQL, CLASSIC);

    static final String QUERY_ID = "query-1";
    static final String TABLE_ID = "table-1";
    static final String TEXT_ID = "text-1";
    // The versions of the settings that the browser's components write, so they don't upgrade what is built here.
    private static final String DASHBOARD_MODEL_VERSION = "7.2.0";
    private static final String TABLE_MODEL_VERSION = "6.1.26";
    private static final String TEXT_MODEL_VERSION = "7.8.0";

    private final Provider<SearchRequestFactory> searchRequestFactoryProvider;
    private final Provider<ExpressionContextFactory> expressionContextFactoryProvider;
    private final Provider<QueryService> queryServiceProvider;

    @Inject
    public DashboardBuilder(final Provider<SearchRequestFactory> searchRequestFactoryProvider,
                            final Provider<ExpressionContextFactory> expressionContextFactoryProvider,
                            final Provider<QueryService> queryServiceProvider) {
        this.searchRequestFactoryProvider = searchRequestFactoryProvider;
        this.expressionContextFactoryProvider = expressionContextFactoryProvider;
        this.queryServiceProvider = queryServiceProvider;
    }

    /// Builds a dashboard's settings from a query.
    ///
    /// @param name      The dashboard's name, used to name its Embedded Query.
    /// @param query     The StroomQL, which must already be valid.
    /// @param queryType [#STROOMQL] or [#CLASSIC].
    /// @param preview   True to add a data preview below the table.
    /// @return The settings, with what they hold, to describe to the user.
    /// @throws IllegalArgumentException saying why, if the dashboard can't be built from the query.
    public Built build(final String name, final String query, final String queryType, final boolean preview) {
        final String type = NullSafe.get(queryType, t -> t.strip().toLowerCase(Locale.ROOT));
        if (type == null || !QUERY_TYPES.contains(type)) {
            throw new IllegalArgumentException("Give the query type: " + String.join(" or ", QUERY_TYPES) + ".");
        }
        final SearchRequest mapped = parse(query);
        final DocRef dataSource = NullSafe.get(mapped, SearchRequest::getQuery, Query::getDataSource);
        final List<TableSettings> tables = NullSafe.list(NullSafe.list(mapped.getResultRequests()).stream()
                .findFirst()
                .map(ResultRequest::getMappings)
                .orElse(null));
        if (dataSource == null || tables.isEmpty()) {
            throw new IllegalArgumentException("The query doesn't give a data source and a table.");
        }
        final TableSettings table = tables.getFirst();
        final List<Column> columns = NullSafe.list(table.getColumns()).stream()
                .filter(column -> !Boolean.TRUE.equals(column.isSpecial()))
                .toList();
        final List<String> columnNames = columns.stream()
                .filter(Column::isVisible)
                .map(Column::getName)
                .filter(Objects::nonNull)
                .toList();
        if (columnNames.isEmpty()) {
            throw new IllegalArgumentException("The query has no select clause, so the table would have no "
                                               + "columns. Give the columns with select. If the user hasn't said "
                                               + "which fields they want, ask them with ask_user, suggesting fields "
                                               + "from describe_data_source.");
        }
        final boolean grouped = columns.stream().anyMatch(column -> column.getGroup() != null);
        if (preview && grouped) {
            throw new IllegalArgumentException("A data preview shows the event a row comes from, which a grouped "
                                               + "row doesn't have. Leave out the preview or the grouping, and say "
                                               + "why to the user.");
        }

        final List<ComponentConfig> components = new ArrayList<>();
        final List<LayoutConfig> panes = new ArrayList<>();
        if (STROOMQL.equals(type)) {
            components.add(ComponentConfig.builder()
                    .type("embedded-query")
                    .id(QUERY_ID)
                    .name("Query")
                    .settings(EmbeddedQueryComponentSettings.builder()
                            .reference(false)
                            .embeddedQueryDoc(QueryDoc.builder()
                                    .uuid(UUID.randomUUID().toString())
                                    .name(name)
                                    .query(query.strip())
                                    .build())
                            .build())
                    .build());
            panes.add(pane(QUERY_ID, 400));
        } else {
            final List<String> problems = classicProblems(tables, table);
            if (!problems.isEmpty()) {
                throw new IllegalArgumentException("A classic dashboard can't hold " + String.join(", ", problems)
                                                   + ". Keep the query as StroomQL, or leave "
                                                   + (problems.size() == 1
                        ? "it"
                        : "them") + " out.");
            }
            final DocRef extractionPipeline = defaultExtractionPipeline(dataSource);
            components.add(ComponentConfig.builder()
                    .type("query")
                    .id(QUERY_ID)
                    .name("Query")
                    .settings(QueryComponentSettings.builder()
                            .dataSource(dataSource)
                            .expression(mapped.getQuery().getExpression())
                            .build())
                    .build());
            components.add(ComponentConfig.builder()
                    .type("table")
                    .id(TABLE_ID)
                    .name("Table")
                    .settings(TableComponentSettings.builder()
                            .queryId(QUERY_ID)
                            .dataSourceRef(dataSource)
                            .columns(new ArrayList<>(columns))
                            .extractValues(extractionPipeline != null)
                            .useDefaultExtractionPipeline(true)
                            .extractionPipeline(extractionPipeline)
                            .maxResults(table.getMaxResults())
                            .modelVersion(TABLE_MODEL_VERSION)
                            .build())
                    .build());
            panes.add(pane(QUERY_ID, 150));
            panes.add(pane(TABLE_ID, 400));
        }
        if (preview) {
            components.add(ComponentConfig.builder()
                    .type("text")
                    .id(TEXT_ID)
                    .name("Data Preview")
                    .settings(TextComponentSettings.builder()
                            // Follows the table, or for StroomQL the Embedded Query's own table.
                            .tableId(CLASSIC.equals(type)
                                    ? TABLE_ID
                                    : null)
                            .streamIdField(new ColumnRef(SpecialColumns.RESERVED_STREAM_ID,
                                    SpecialColumns.RESERVED_STREAM_ID))
                            .recordNoField(new ColumnRef(SpecialColumns.RESERVED_EVENT_ID,
                                    SpecialColumns.RESERVED_EVENT_ID))
                            .modelVersion(TEXT_MODEL_VERSION)
                            .build())
                    .build());
            panes.add(pane(TEXT_ID, 250));
        }

        final DashboardConfig config = DashboardConfig.builder()
                .components(components)
                .layout(SplitLayoutConfig.builder()
                        .preferredSize(new Size(200, 200))
                        .dimension(Dimension.Y)
                        .children(panes)
                        .build())
                .designMode(false)
                .modelVersion(DASHBOARD_MODEL_VERSION)
                .build();
        final List<String> outline = CLASSIC.equals(type)
                ? classicOutline(dataSource, mapped.getQuery().getExpression(), columns, table.getMaxResults(),
                preview)
                : List.of();
        return new Built(config, dataSource, type, columnNames, grouped, preview, outline);
    }

    /// @return A classic dashboard in plain words, as its Query and Table hold it, for the user to see before they
    /// create it: what the query selects, the table's columns, grouping and sorting, and how many rows it shows.
    static List<String> classicOutline(final DocRef dataSource,
                                       final ExpressionOperator expression,
                                       final List<Column> columns,
                                       final List<Long> maxResults,
                                       final boolean preview) {
        final List<String> outline = new ArrayList<>();
        outline.add("Query: " + NullSafe.nonBlankStringElse(ExpressionDescriber.describe(expression),
                "everything") + ", from " + dataSource.getType() + " '" + dataSource.getName() + "'");
        outline.add("Table columns: " + String.join(", ", columns.stream()
                .filter(Column::isVisible)
                .map(Column::getName)
                .toList()));
        final List<String> groups = columns.stream()
                .filter(column -> column.getGroup() != null)
                .sorted(Comparator.comparing(Column::getGroup))
                .map(Column::getName)
                .toList();
        if (!groups.isEmpty()) {
            outline.add("Grouped by: " + String.join(", ", groups));
        }
        final List<String> sorts = columns.stream()
                .filter(column -> column.getSort() != null)
                .sorted(Comparator.comparing(column -> Objects.requireNonNullElse(column.getSort().getOrder(), 0)))
                .map(column -> column.getName() + " (" + (column.getSort().getDirection() == SortDirection.DESCENDING
                        ? "descending"
                        : "ascending") + ")")
                .toList();
        if (!sorts.isEmpty()) {
            outline.add("Sorted by: " + String.join(", ", sorts));
        }
        final Long limit = NullSafe.list(maxResults).stream().findFirst().orElse(null);
        if (limit != null) {
            outline.add("At most " + limit + " rows");
        }
        if (preview) {
            outline.add("A data preview of the selected row's event");
        }
        return List.copyOf(outline);
    }

    private SearchRequest parse(final String query) {
        final SearchRequest sample = new SearchRequest(null, null, Query.builder().build(), null, null, false, null);
        try {
            return searchRequestFactoryProvider.get().create(query, sample,
                    expressionContextFactoryProvider.get().createContext(sample));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the query: " + e.getMessage(), e);
            throw new IllegalArgumentException("The query can't be read: "
                                               + NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getName()));
        }
    }

    // What a classic dashboard can't hold, as StroomQL names it.
    private static List<String> classicProblems(final List<TableSettings> tables, final TableSettings table) {
        final List<String> problems = new ArrayList<>();
        if (table.getWindow() != null) {
            problems.add("a window");
        }
        if (table.getValueFilter() != null) {
            problems.add("a filter clause");
        }
        if (table.getAggregateFilter() != null) {
            problems.add("a having clause");
        }
        if (tables.size() > 1 || table.getVisSettings() != null) {
            problems.add("a show clause (a visualisation)");
        }
        return problems;
    }

    private DocRef defaultExtractionPipeline(final DocRef dataSource) {
        try {
            return queryServiceProvider.get().fetchDefaultExtractionPipeline(dataSource).orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the default extraction pipeline of " + dataSource, e);
            return null;
        }
    }

    private static TabLayoutConfig pane(final String componentId, final int height) {
        return TabLayoutConfig.builder()
                .preferredSize(new Size(200, height))
                .tabs(List.of(new TabConfig(componentId, true)))
                .selected(0)
                .build();
    }

    /// The settings of a new dashboard, and what they hold.
    ///
    /// @param config     The dashboard's settings.
    /// @param dataSource The data source its query reads.
    /// @param queryType  [#STROOMQL] or [#CLASSIC].
    /// @param columns    The names of the table's visible columns.
    /// @param grouped    True if the table groups rows.
    /// @param preview    True if it has a data preview.
    /// @param outline    For a classic dashboard, what its Query and Table hold, in plain words, as the user is
    ///                   shown it in place of the StroomQL it was built from; empty for StroomQL.
    public record Built(DashboardConfig config,
                        DocRef dataSource,
                        String queryType,
                        List<String> columns,
                        boolean grouped,
                        boolean preview,
                        List<String> outline) {

        /// @return What the dashboard holds, in a sentence, e.g. "A classic query and a table of 3 columns (EventTime,
        /// User, Action), with a data preview".
        public String describe() {
            return (CLASSIC.equals(queryType)
                    ? "A classic (expression) query and a table"
                    : "A StroomQL query, with its table,")
                   + " of " + columns.size() + " column" + (columns.size() == 1
                    ? ""
                    : "s") + " (" + String.join(", ", columns) + ")"
                   + (grouped
                    ? ", grouped"
                    : "")
                   + (preview
                    ? ", with a data preview"
                    : "");
        }
    }
}
