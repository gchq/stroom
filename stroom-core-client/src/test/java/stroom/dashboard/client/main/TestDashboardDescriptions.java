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

package stroom.dashboard.client.main;

import stroom.ai.shared.AiTabSnapshotPart;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDashboardDescriptions {

    private static final String DASHBOARD = """
            Time range: All time
            Shown components: Query, Events

            ## Query 'Query' (id query-1)
            Expression:
            AND

            ## Table 'Events' (id table-1)
            Query: query-1
            Columns:
            - UserId: ${UserId}

            ## Visualisation 'Chart (by user)' (id vis-1)
            Linked table: 'Events'
            """;

    @Test
    void eachComponentsPartIsReadByItsId() {
        assertThat(DashboardDescriptions.componentParts(DASHBOARD))
                .containsOnlyKeys("query-1", "table-1", "vis-1")
                .containsEntry("table-1", "## Table 'Events' (id table-1)\nQuery: query-1\nColumns:\n"
                                          + "- UserId: ${UserId}\n")
                .containsEntry("vis-1", "## Visualisation 'Chart (by user)' (id vis-1)\nLinked table: 'Events'\n");
        assertThat(DashboardDescriptions.componentParts(null)).isEmpty();
    }

    @Test
    void changesElsewhereDontStopAnAppliedChangeBeingUndone() {
        // Seen in testing: a change couldn't be undone, or applied, once anything on the dashboard had changed.
        final String tableChanged = DASHBOARD.replace("- UserId: ${UserId}", "- UserId: ${UserId}\n- Host: ${Host}");
        final String otherAdded = DASHBOARD + "\n## Text 'Preview' (id text-1)\n";

        assertThat(DashboardDescriptions.sameComponents(DASHBOARD, tableChanged, List.of("vis-1", "query-1")))
                .isTrue();
        assertThat(DashboardDescriptions.sameComponents(DASHBOARD, otherAdded, List.of("table-1", "vis-1")))
                .isTrue();
        assertThat(DashboardDescriptions.sameComponents(DASHBOARD, tableChanged, List.of("vis-1", "table-1")))
                .isFalse();
        // A component the change added, since removed.
        assertThat(DashboardDescriptions.sameComponents(otherAdded, DASHBOARD, List.of("text-1"))).isFalse();
    }

    @Test
    void aQueryExpressionIsTheSameIfDescribedTheSame() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field("EventType").condition(Condition.EQUALS).value("Logon")
                        .build())
                .build();
        // As the server reads it from the dashboard's description.
        final String proposed = AiTabSnapshotPart.hashLines(expression.toMultiLineString());

        assertThat(DashboardDescriptions.sameExpression(proposed, expression)).isTrue();
        assertThat(DashboardDescriptions.sameExpression(proposed.toUpperCase(), expression)).isTrue();
        assertThat(DashboardDescriptions.sameExpression(proposed, expression.copy()
                .addTerm(ExpressionTerm.builder().field("UserId").condition(Condition.EQUALS).value("alice")
                        .build())
                .build())).isFalse();
        assertThat(DashboardDescriptions.sameExpression(proposed, null)).isFalse();
        assertThat(DashboardDescriptions.sameExpression(AiTabSnapshotPart.hashLines("null"), null)).isTrue();
    }

    @Test
    void aFilterAddedWithItsChartNamesTheChartByItsIdOnceAdded() {
        final ExpressionOperator planned = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder().field("Day").condition(Condition.GREATER_THAN_OR_EQUAL_TO)
                        .value("${component.new-chart.selection.from}").build())
                .addOperator(ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("User").condition(Condition.EQUALS)
                                .value("${component.new-chart.selection.series}").build())
                        .build())
                .build();

        final ExpressionOperator added = DashboardDescriptions.withChartId(planned, "new-chart", "vis-AB12C");

        assertThat(added.toString())
                .contains("${component.vis-AB12C.selection.from}")
                .contains("${component.vis-AB12C.selection.series}")
                .doesNotContain("new-chart");
        assertThat(added.op()).isEqualTo(Op.OR);
        assertThat(DashboardDescriptions.withChartId(planned, "vis-1", "vis-1")).isSameAs(planned);
        assertThat(DashboardDescriptions.withChartId(null, "new-chart", "vis-1")).isNull();
    }

    @Test
    void aConvertedChartsSelectionKeysAreRenamedInItsFilters() {
        final ExpressionOperator filter = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder().field("Day").condition(Condition.GREATER_THAN_OR_EQUAL_TO)
                        .value("${component.vis-1.selection.from}").build())
                .addOperator(ExpressionOperator.builder()
                        .addTerm(ExpressionTerm.builder().field("User").condition(Condition.EQUALS)
                                .value("${component.vis-1.selection.x}").build())
                        .addTerm(ExpressionTerm.builder().field("Kind").condition(Condition.EQUALS)
                                .value("${component.vis-1.selection.category}").build())
                        .addTerm(ExpressionTerm.builder().field("Host").condition(Condition.EQUALS)
                                .value("${component.vis-1.selection.xy}").build())
                        .addTerm(ExpressionTerm.builder().field("Other").condition(Condition.EQUALS)
                                .value("${component.vis-2.selection.x}").build())
                        .build())
                .build();
        // Swapped at once, not one after the other.
        final Map<String, String> renamed = Map.of("x", "category", "category", "series");

        final ExpressionOperator converted = DashboardDescriptions.withSelections(filter, "vis-1", renamed);

        assertThat(converted.toString())
                .contains("User = ${component.vis-1.selection.category}")
                // Only whole keys, of this chart.
                .contains("Host = ${component.vis-1.selection.xy}")
                .contains("Other = ${component.vis-2.selection.x}")
                .contains("Kind = ${component.vis-1.selection.series}")
                .contains("${component.vis-1.selection.from}");
        assertThat(converted.op()).isEqualTo(Op.OR);
        assertThat(DashboardDescriptions.withSelections(filter, "vis-3", renamed)).isSameAs(filter);
        assertThat(DashboardDescriptions.withSelections(filter, "vis-1", Map.of())).isSameAs(filter);
        assertThat(DashboardDescriptions.withSelections(null, "vis-1", renamed)).isNull();
    }

    @Test
    void renamedKeysAreReadAsTheServerGivesThem() {
        assertThat(DashboardDescriptions.renamedKeys("x=category, series=name"))
                .containsOnly(Map.entry("x", "category"), Map.entry("series", "name"));
        assertThat(DashboardDescriptions.renamedKeys("broken,=name,x=")).isEmpty();
        assertThat(DashboardDescriptions.renamedKeys(null)).isEmpty();
    }
}
