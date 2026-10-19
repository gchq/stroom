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

import stroom.ai.impl.DashboardBuilder.Built;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.SpecialColumns;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.impl.QueryService;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestFactory;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDashboardBuilder {

    private static final DocRef VIEW = new DocRef("View", "view-uuid", "My View");
    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Extraction");

    private final QueryService queryService = Mockito.mock(QueryService.class);
    private DashboardBuilder builder;

    @BeforeEach
    void setUp() {
        final DataSourceResolver dataSourceResolver = Mockito.mock(DataSourceResolver.class);
        Mockito.when(dataSourceResolver.resolveDataSourceRef(Mockito.anyString())).thenReturn(VIEW);
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.useAsReadResult(Mockito.any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        final SearchRequestFactory factory = new SearchRequestFactory((keywordGroup, parentTableSettings) -> null,
                dataSourceResolver, () -> criteria -> null, securityContext);
        Mockito.when(queryService.fetchDefaultExtractionPipeline(VIEW)).thenReturn(Optional.of(PIPELINE));
        builder = new DashboardBuilder(() -> factory, ExpressionContextFactory::new, () -> queryService);
    }

    @Test
    void testStroomQlIsKeptInAnEmbeddedQuery() {
        final String query = "from \"My View\" where EventTime > day() - 1d select EventTime, User";

        final Built built = builder.build("Logons", query, "StroomQL", false);

        assertThat(built.queryType()).isEqualTo(DashboardBuilder.STROOMQL);
        assertThat(built.dataSource()).isEqualTo(VIEW);
        assertThat(built.columns()).containsExactly("EventTime", "User");
        final List<ComponentConfig> components = built.config().getComponents();
        assertThat(components).extracting(ComponentConfig::getType).containsExactly("embedded-query");
        final EmbeddedQueryComponentSettings settings = (EmbeddedQueryComponentSettings) components.getFirst()
                .getSettings();
        assertThat(settings.reference()).isFalse();
        assertThat(settings.getEmbeddedQueryDoc().getQuery()).isEqualTo(query);
        assertThat(settings.getEmbeddedQueryDoc().getName()).isEqualTo("Logons");
        assertThat(((SplitLayoutConfig) built.config().getLayout()).getChildren()).hasSize(1);
        assertThat(built.describe()).isEqualTo("A StroomQL query, with its table, of 2 columns (EventTime, User)");
    }

    @Test
    void testAPreviewFollowsTheEmbeddedQuerysTable() {
        final Built built = builder.build("Logons", "from \"My View\" select EventTime, User", "stroomql", true);

        final List<ComponentConfig> components = built.config().getComponents();
        assertThat(components).extracting(ComponentConfig::getType).containsExactly("embedded-query", "text");
        final TextComponentSettings text = (TextComponentSettings) components.get(1).getSettings();
        // Any table: the Embedded Query isn't a Table, so the preview can't name it.
        assertThat(text.getTableId()).isNull();
        assertThat(text.getStreamIdColumn().getId()).isEqualTo(SpecialColumns.RESERVED_STREAM_ID);
        assertThat(text.getRecordNoColumn().getId()).isEqualTo(SpecialColumns.RESERVED_EVENT_ID);
        assertThat(((SplitLayoutConfig) built.config().getLayout()).getChildren()).hasSize(2);
        assertThat(built.describe()).endsWith(", with a data preview");
    }

    @Test
    void testClassicTurnsTheQueryIntoAnExpressionAndATable() {
        final Built built = builder.build("Logons",
                "from \"My View\" where User = \"jbloggs\" sort by EventTime desc limit 50 select EventTime, User",
                DashboardBuilder.CLASSIC, true);

        final List<ComponentConfig> components = built.config().getComponents();
        assertThat(components).extracting(ComponentConfig::getType).containsExactly("query", "table", "text");
        final QueryComponentSettings query = (QueryComponentSettings) components.get(0).getSettings();
        assertThat(query.getDataSource()).isEqualTo(VIEW);
        assertThat(query.getExpression().getChildren()).singleElement().satisfies(item -> {
            final ExpressionTerm term = (ExpressionTerm) item;
            assertThat(term.getField()).isEqualTo("User");
            assertThat(term.getValue()).isEqualTo("jbloggs");
        });
        final TableComponentSettings table = (TableComponentSettings) components.get(1).getSettings();
        assertThat(table.getQueryId()).isEqualTo(DashboardBuilder.QUERY_ID);
        assertThat(table.getDataSourceRef()).isEqualTo(VIEW);
        // The table adds the special columns itself.
        assertThat(table.getColumns()).extracting(Column::getName).containsExactly("EventTime", "User");
        assertThat(table.getColumns().getFirst().getSort()).isNotNull();
        assertThat(table.getExtractionPipeline()).isEqualTo(PIPELINE);
        assertThat(table.extractValues()).isTrue();
        assertThat(table.getMaxResults()).contains(50L);
        final TextComponentSettings text = (TextComponentSettings) components.get(2).getSettings();
        assertThat(text.getTableId()).isEqualTo(DashboardBuilder.TABLE_ID);
        assertThat(built.describe()).startsWith("A classic (expression) query and a table of 2 columns");
        assertThat(built.outline()).containsExactly(
                "Query: User is 'jbloggs', from View 'My View'",
                "Table columns: EventTime, User",
                "Sorted by: EventTime (descending)",
                "At most 50 rows",
                "A data preview of the selected row's event");
    }

    @Test
    void testClassicKeepsGrouping() {
        final Built built = builder.build("Counts",
                "from \"My View\" eval Count = count() group by User select User, Count",
                DashboardBuilder.CLASSIC, false);

        final TableComponentSettings table = (TableComponentSettings) built.config().getComponents().get(1)
                .getSettings();
        assertThat(table.getColumns()).extracting(Column::getName).containsExactly("User", "Count");
        assertThat(table.getColumns().getFirst().getGroup()).isEqualTo(0);
        assertThat(built.grouped()).isTrue();
        assertThat(built.describe()).contains(", grouped");
        // A query with no where selects everything.
        assertThat(built.outline()).containsExactly(
                "Query: everything, from View 'My View'",
                "Table columns: User, Count",
                "Grouped by: User");
    }

    @Test
    void testClassicRefusesWhatItCantHold() {
        assertThatThrownBy(() -> builder.build("Counts",
                "from \"My View\" eval Count = count() group by User having Count > 5 select User, Count",
                DashboardBuilder.CLASSIC, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a having clause")
                .hasMessageContaining("Keep the query as StroomQL");
    }

    @Test
    void testStroomQlHoldsWhatClassicCant() {
        final Built built = builder.build("Counts",
                "from \"My View\" eval Count = count() group by User having Count > 5 select User, Count",
                DashboardBuilder.STROOMQL, false);

        assertThat(built.columns()).containsExactly("User", "Count");
        // The user is shown the StroomQL itself.
        assertThat(built.outline()).isEmpty();
    }

    @Test
    void testATableNeedsColumns() {
        assertThatThrownBy(() -> builder.build("Logons", "from \"My View\" where User = \"jbloggs\"",
                DashboardBuilder.STROOMQL, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no select clause")
                .hasMessageContaining("ask_user");
    }

    @Test
    void testAPreviewNeedsUngroupedRows() {
        assertThatThrownBy(() -> builder.build("Counts",
                "from \"My View\" eval Count = count() group by User select User, Count",
                DashboardBuilder.STROOMQL, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data preview");
    }

    @Test
    void testTheQueryTypeMustBeKnown() {
        assertThatThrownBy(() -> builder.build("Logons", "from \"My View\" select User", "lucene", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stroomql or classic");
        assertThatThrownBy(() -> builder.build("Logons", "from \"My View\" select User", null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAQueryThatCantBeReadIsRefused() {
        assertThatThrownBy(() -> builder.build("Logons", "select from where", DashboardBuilder.STROOMQL, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The query can't be read");
    }

    @Test
    void testConditionsBecomeAClassicQuerysExpression() {
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));

        final ExpressionOperator expression = builder.whereExpression(VIEW,
                "User = \"jbloggs\" or User = \"asmith\"");

        assertThat(expression.getChildren()).extracting(item -> ((ExpressionTerm) item).getValue())
                .containsExactly("jbloggs", "asmith");
        // Checked as StroomQL is, against the data source.
        Mockito.verify(queryService).validateQuery("from \"My View\" where User = \"jbloggs\" or User = \"asmith\"");
    }

    @Test
    void testConditionsThatArentValidAreRefused() {
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(false, "Unknown field 'Usr'", false));

        assertThatThrownBy(() -> builder.whereExpression(VIEW, "Usr = \"x\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The conditions aren't valid: Unknown field 'Usr'");
        assertThatThrownBy(() -> builder.whereExpression(VIEW, " "))
                .hasMessage("Give the query's conditions.");
        assertThatThrownBy(() -> builder.whereExpression(null, "User = \"x\""))
                .hasMessageContaining("data source isn't known");
    }

    @Test
    void testNoExtractionPipelineMeansNoExtraction() {
        Mockito.when(queryService.fetchDefaultExtractionPipeline(VIEW)).thenReturn(Optional.empty());

        final Built built = builder.build("Logons", "from \"My View\" select User", DashboardBuilder.CLASSIC,
                false);

        final TableComponentSettings table = (TableComponentSettings) built.config().getComponents().get(1)
                .getSettings();
        assertThat(table.getExtractionPipeline()).isNull();
        assertThat(table.extractValues()).isFalse();
    }
}
