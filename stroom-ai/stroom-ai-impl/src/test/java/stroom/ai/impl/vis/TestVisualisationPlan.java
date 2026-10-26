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

package stroom.ai.impl.vis;

import stroom.ai.impl.vis.VisualisationPlan.Replace;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.json.JsonUtil;
import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVisualisationPlan {

    private static VisualisationPlan dashboardPlan(final Map<String, String> style, final String options) {
        return new VisualisationPlan("graph", Map.of("source", "Sender", "target", "Recipient"), style, options,
                "Messages", "table-ABC12", "Chat user connections", VisualisationPlan.BELOW, "abc", null, null);
    }

    @Test
    void roundTripsThroughJson() {
        final VisualisationPlan plan = dashboardPlan(Map.of("directed", "true"), "{\"color\":[\"#000\"]}");

        final VisualisationPlan read = VisualisationPlan.fromJson(plan.toJson());

        assertThat(read).isEqualTo(plan);
        assertThat(read.chartKind()).isEqualTo(ChartKind.GRAPH);
        assertThat(read.isQuery()).isFalse();
    }

    @Test
    void refusesAnUnknownKindOrRole() {
        assertThatThrownBy(() -> new VisualisationPlan("radar", Map.of(), null, null, "t", "id", "n", null, null,
                null, null).validate())
                .hasMessageContaining("Unknown chart kind");
        assertThatThrownBy(() -> new VisualisationPlan("pie", Map.of("colour", "x"), null, null, "t", "id", "n",
                null, null, null, null).validate())
                .hasMessageContaining("has no 'colour'");
    }

    @Test
    void normalisesStyle() {
        assertThat(VisualisationPlan.normaliseStyle(ChartKind.BAR,
                Map.of("horizontal", "Yes", "sort", "descending", "maxSeries", " 5 ")))
                .containsEntry("horizontal", "true")
                .containsEntry("sort", "Descending")
                .containsEntry("maxSeries", "5");
    }

    @Test
    void aThresholdTakesAnyNumberOrBlank() {
        assertThat(VisualisationPlan.normaliseStyle(ChartKind.BAR, Map.of("threshold", " 1.5e3 ")))
                .containsEntry("threshold", "1.5e3");
        assertThat(VisualisationPlan.normaliseStyle(ChartKind.LINE, Map.of("threshold", "")))
                .containsEntry("threshold", "");
        assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(ChartKind.SCATTER, Map.of("threshold", "high")))
                .hasMessageContaining("give a number, or blank for none");
        assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(ChartKind.BAR, Map.of("threshold", "NaN")))
                .hasMessageContaining("give a number, or blank for none");
        // Not a chart without a value axis.
        assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(ChartKind.PIE, Map.of("threshold", "1")))
                .hasMessageContaining("has no setting 'threshold'");
        assertThat(VisualisationPlan.normaliseStyle(ChartKind.GRAPH, Map.of("physics", "Lively", "thumbnail", "on")))
                .containsEntry("physics", "lively")
                .containsEntry("thumbnail", "true");
    }

    @Test
    void refusesStyleTheKindDoesntHave() {
        assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(ChartKind.PIE, Map.of("horizontal", "true")))
                .hasMessageContaining("has no setting 'horizontal'");
        assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(ChartKind.GRAPH, Map.of("maxNodes", "1")))
                .hasMessageContaining("a whole number from 2 to 5000");
        assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(ChartKind.BAR, Map.of("legend", "left")))
                .hasMessageContaining("one of top, bottom, right, none");
    }

    @Test
    void refusesATitleAsTheDashboardShowsTheComponentsName() {
        for (final ChartKind kind : ChartKind.values()) {
            assertThat(kind.styleSettings()).as(kind.name())
                    .noneMatch(setting -> "title".equals(setting.id()));
            assertThatThrownBy(() -> VisualisationPlan.normaliseStyle(kind, Map.of("title", "Events")))
                    .as(kind.name())
                    .hasMessageContaining("has no setting 'title'");
        }
        assertThatThrownBy(() -> VisualisationPlan.normaliseOptions("{\"title\": {\"text\": \"Events\"}}"))
                .hasMessageContaining("can't set title");
    }

    @Test
    void checksOptions() {
        assertThat(VisualisationPlan.normaliseOptions(null)).isNull();
        assertThat(VisualisationPlan.normaliseOptions(" { \"color\" : [\"#000\"] } "))
                .isEqualTo("{\"color\":[\"#000\"]}");
        assertThatThrownBy(() -> VisualisationPlan.normaliseOptions("[1, 2]"))
                .hasMessageContaining("must be a JSON object");
        assertThatThrownBy(() -> VisualisationPlan.normaliseOptions("{not json"))
                .hasMessageContaining("must be a JSON object");
        assertThatThrownBy(() -> VisualisationPlan.normaliseOptions("{\"series\": []}"))
                .hasMessageContaining("can't set series");
    }

    @Test
    void dashboardPlanNeedsItsTableAndComponentName() {
        assertThatThrownBy(() -> new VisualisationPlan("bar", Map.of(), null, null, "Messages", null, "Chart",
                null, null, null, null).validate())
                .hasMessageContaining("needs its table");
        // A query's plan needs neither.
        new VisualisationPlan("bar", Map.of(), null, null, null, null, null, null, "h", true, "from x").validate();
    }

    @Test
    void refusesABadPlacementOrControlCharacters() {
        assertThatThrownBy(() -> new VisualisationPlan("bar", Map.of(), null, null, "t", "id", "n", "left", null,
                null, null).validate())
                .hasMessageContaining("Placement");
        assertThatThrownBy(() -> new VisualisationPlan("bar", Map.of("category", "a\u0001b"), null, null, "t",
                "id", "n", null, null, null, null).validate())
                .hasMessageContaining("control characters");
    }

    @Test
    void aReplacementRoundTripsAndIsChecked() {
        final Replace replace = new Replace("vis-1", "Old chart", new DocRef(VisualisationDoc.TYPE, "v1", "Bars"));
        final VisualisationPlan plan = new VisualisationPlan("line", Map.of("x", "Hour", "y", "Count"), Map.of(),
                null, "Events", "table-1", "Old chart", null, "h", null, null, replace);

        final VisualisationPlan read = VisualisationPlan.fromJson(plan.toJson());

        assertThat(read).isEqualTo(plan);
        assertThat(read.isReplacement()).isTrue();
        assertThat(dashboardPlan(Map.of(), null).isReplacement()).isFalse();
        assertThat(dashboardPlan(Map.of(), null).toJson()).doesNotContain("replace");
        // Only on a dashboard, and only of a Visualisation in a vis component.
        assertThatThrownBy(() -> new VisualisationPlan("bar", Map.of(), null, null, null, null, null, null, "h",
                true, "from x", replace).validate())
                .hasMessageContaining("Only a chart on a dashboard can replace another");
        assertThatThrownBy(() -> new VisualisationPlan("bar", Map.of(), null, null, "t", "id", "n", null, "h",
                null, null, new Replace("", "Old chart", replace.visualisation())).validate())
                .hasMessageContaining("needs the vis component and the Visualisation");
        assertThatThrownBy(() -> new VisualisationPlan("bar", Map.of(), null, null, "t", "id", "n", null, "h",
                null, null, new Replace("vis-1", "Old chart", new DocRef("Script", "s1", "x"))).validate())
                .hasMessageContaining("needs the vis component and the Visualisation");
    }

    @Test
    void anEmbeddedQuerysPlanIsAShowClauseForItsComponent() {
        final VisualisationPlan plan = new VisualisationPlan("pie", Map.of("name", "UserA", "value", "Messages"),
                Map.of(), null, "Pairs", "query-1", null, null, "h", true, "from x select UserA, Messages");

        assertThat(plan.isQuery()).isTrue();
        assertThat(plan.isEmbeddedQuery()).isTrue();
        assertThat(plan.toJson()).doesNotContain("embeddedQuery");
        assertThat(VisualisationPlan.fromJson(plan.toJson())).isEqualTo(plan);
        // A Query's plan names no component.
        assertThat(new VisualisationPlan("pie", Map.of(), Map.of(), null, null, null, null, null, "h", true,
                "from x").isEmbeddedQuery()).isFalse();
    }

    @Test
    void replacementSettingsClearTheOldChartsColumns() {
        @SuppressWarnings("unchecked")
        final Map<String, Object> settings = JsonUtil.readValue(VisualisationActions.replacementSettings(
                ChartKind.BAR, Map.of("category", " Hour ", "value", "Count")), Map.class);

        assertThat(settings)
                .containsEntry("category", "Hour")
                .containsEntry("value", "Count")
                // Not mapped: back to its default.
                .containsEntry("series", "")
                // A line chart's columns, which the old chart may have had.
                .containsEntry("x", "")
                .containsEntry("y", "")
                .containsEntry("source", "")
                // Seen in testing: a line chart made a bar chart kept the pane's kind, field order and style, so
                // asked for x and y. They are cleared, so the rewritten Visualisation's own apply.
                .containsEntry("kind", "")
                .containsEntry("fields", "")
                .containsEntry("options", "")
                .containsEntry("stack", "")
                .containsEntry("zoomSlider", "")
                .containsEntry("smooth", "");
    }

    @Test
    void theSuggestedFolderIsKeyedAsTheExplorerTreeKeysIt() {
        final ExplorerNode folder = ExplorerNode.builder().type("Folder").uuid("f1").name("Reports").build();

        assertThat(folder.getUniqueKey().getRootNodeUuid()).isNull();
        assertThat(VisualisationActions.inExplorerTree(folder).getUniqueKey())
                .isEqualTo(new ExplorerNodeKey("Folder", "f1", ExplorerConstants.SYSTEM_DOC_REF.getUuid()));
    }

    @Test
    void componentSettingsAndShowArgumentsFollowTheRoles() {
        final Map<String, String> mappings = Map.of("target", "Recipient", "source", "Sender", "weight", "");

        assertThat(VisualisationActions.componentSettings(ChartKind.GRAPH, mappings))
                .isEqualTo("{\"source\":\"Sender\",\"target\":\"Recipient\"}");
        assertThat(VisualisationActions.showArguments(ChartKind.GRAPH, mappings))
                .isEqualTo("source = \"Sender\", target = \"Recipient\"");
        assertThat(VisualisationActions.quote("a \"b\" \\c")).isEqualTo("\"a \\\"b\\\" \\\\c\"");
    }
}
