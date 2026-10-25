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
import stroom.ai.shared.AiCreateVisualisation;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.VisResultRequest;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.explorer.shared.PermissionInheritance;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.QLVisResult;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableSettings;
import stroom.query.api.VisResult;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.script.shared.ScriptDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.DocumentPermission;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.DocPath;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// End to end on the test database: an operator installs ECharts as a Script doc; the agent's planned network
/// graph is prepared as the "Create visualisation" button prepares it (putting Stroom's adapter beside ECharts);
/// the user creates the Visualisation in the suggested folder, filled as the browser fills it; and a StroomQL query
/// with the planned `show` clause gives the chart its data through Stroom's own visualisation pipeline.
class TestVisualisationE2e extends AbstractAiIntegrationTest {

    // Stands in for an ECharts build: Stroom only recognises it by its content, the browser runs it.
    private static final String ECHARTS = "/* Apache ECharts */ !function(t){t.echarts={}}(this);"
                                          + "t.dependencies={zrender:\"6.1.0\"},t.version=\"6.1.0\";";
    private static final String QUERY = """
            from Dual
            eval Sender = 'alice'
            eval Recipient = 'bob'
            eval Count = 3
            select Sender, Recipient, Count""";

    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private ScriptStore scriptStore;
    @Inject
    private VisualisationStore visualisationStore;
    @Inject
    private ChartLibraries chartLibraries;
    @Inject
    private VisualisationActions visualisationActions;
    @Inject
    private QueryService queryService;
    @Inject
    private DashboardService dashboardService;
    @Inject
    private DocumentPermissionService documentPermissionService;

    @Test
    void generatedNetworkGraphDrawsAStroomQlQuery() {
        final UserRef admin = administrator();
        asUser(admin, () -> {
            // An operator installs ECharts.
            final ExplorerNode libraries = folder("Visualisations", "ECharts");
            final DocRef echarts = create(ScriptDoc.TYPE, "ECharts", libraries);
            scriptStore.writeDocument(scriptStore.readDocument(echarts).copy().data(ECHARTS).build());
            assertThat(chartLibraries.findECharts()).hasValueSatisfying(install -> {
                assertThat(install.script()).isEqualTo(echarts);
                assertThat(install.version()).isEqualTo("6.1.0");
            });

            // The query the user attached, in its own folder.
            final ExplorerNode charts = folder("Charts");
            final DocRef query = create(QueryDoc.TYPE, "Messages", charts);

            // The button is clicked.
            final VisualisationPlan plan = new VisualisationPlan("graph",
                    Map.of("source", "Sender", "target", "Recipient", "weight", "Count"),
                    Map.of("directed", "true"), null, null, null, null, null, "hash", true, QUERY);
            final AiCreateVisualisation create = visualisationActions.prepare(query, "Chat user connections",
                    plan.toJson());

            // It suggests the query's folder, and draws with the adapter, put beside ECharts and depending on it.
            assertThat(create.getFolder().getDocRef()).isEqualTo(charts.getDocRef());
            // Keyed as the explorer tree keys it, under the System root, so the New Visualisation dialog selects it.
            assertThat(create.getFolder().getUniqueKey()).isEqualTo(new ExplorerNodeKey(charts.getType(),
                    charts.getUuid(), ExplorerConstants.SYSTEM_DOC_REF.getUuid()));
            assertThat(create.getFunctionName()).isEqualTo(EChartsTemplate.FUNCTION_NAME);
            assertThat(explorerNodeService.getParent(create.getScriptRef()).orElseThrow().getDocRef())
                    .isEqualTo(libraries.getDocRef());
            final ScriptDoc adapter = scriptStore.readDocument(create.getScriptRef());
            assertThat(adapter.getDependencies()).containsExactly(echarts);
            assertThat(adapter.getData()).isEqualTo(chartLibraries.adapterSource());
            assertThat(create.getShowArguments())
                    .isEqualTo("source = \"Sender\", target = \"Recipient\", weight = \"Count\"");

            // A second click finds the adapter rather than making another.
            assertThat(visualisationActions.prepare(query, "Again", plan.toJson()).getScriptRef())
                    .isEqualTo(create.getScriptRef());

            // The user creates it where suggested, and the browser fills it.
            final DocRef visualisation = create(VisualisationDoc.TYPE, "Chat user connections", create.getFolder());
            visualisationStore.writeDocument(visualisationStore.readDocument(visualisation).copy()
                    .functionName(create.getFunctionName())
                    .scriptRef(create.getScriptRef())
                    .settings(create.getSettings())
                    .description(create.getDescription())
                    .build());

            // The query, with the show clause the browser puts in it, naming the Visualisation by its UUID.
            // Another Visualisation of the same name, which the UUID in the clause isn't confused by.
            create(VisualisationDoc.TYPE, "Chat user connections", charts);
            final QLVisResult result = visResult(QUERY + "\nshow " + VisualisationActions.quote(
                    visualisation.getUuid()) + " (" + create.getShowArguments() + ")");

            final Map<String, Object> data = readMap(result.getJsonData());
            // One row, its values in the order the adapter reads them: source, target, weight, then the two
            // unmapped groups.
            final List<?> rows = (List<?>) data.get("values");
            assertThat(rows).hasSize(1);
            assertThat(((List<?>) rows.getFirst()).subList(0, 3)).isEqualTo(List.of("alice", "bob", 3.0));
            assertThat((List<?>) data.get("types")).hasSize(5);
            final Map<String, Object> settings = readMap(result.getVisSettings().getJson());
            assertThat(settings).containsEntry("source", "Sender").containsEntry("weight", "Count");
        });
    }

    @Test
    void generatedBarChartDrawsADashboardTable() {
        final UserRef admin = administrator();
        asUser(admin, () -> {
            // An operator installs ECharts.
            final ExplorerNode libraries = folder("Visualisations", "ECharts");
            final DocRef echarts = create(ScriptDoc.TYPE, "ECharts", libraries);
            scriptStore.writeDocument(scriptStore.readDocument(echarts).copy().data(ECHARTS).build());

            // The dashboard the user attached, in its own folder, with a table fed by a query on Dual.
            final ExplorerNode reports = folder("Reports");
            final DocRef dashboard = create(DashboardDoc.TYPE, "Chats", reports);
            final TableSettings table = TableSettings.builder()
                    .addColumns(Column.builder().id("c1").name("Sender").expression("'alice'")
                            .format(Format.TEXT).build())
                    .addColumns(Column.builder().id("c2").name("Count").expression("3")
                            .format(Format.NUMBER).build())
                    .addColumns(Column.builder().id("c3").name("Team").expression("'Sales'")
                            .format(Format.TEXT).build())
                    .addMaxResults(1000L)
                    .build();

            // The button is clicked.
            final VisualisationPlan plan = new VisualisationPlan("bar",
                    Map.of("category", "Sender", "value", "Count", "series", "Team"),
                    Map.of("horizontal", "true"), null, "Messages", "table-1", "Messages per sender",
                    VisualisationPlan.BELOW, "hash", null, null);
            final AiCreateVisualisation create = visualisationActions.prepare(dashboard, "Messages per sender",
                    plan.toJson());

            assertThat(create.getFolder().getDocRef()).isEqualTo(reports.getDocRef());
            assertThat(create.isForQuery()).isFalse();
            assertThat(create.getTableId()).isEqualTo("table-1");
            assertThat(create.getComponentSettings())
                    .isEqualTo("{\"category\":\"Sender\",\"value\":\"Count\",\"series\":\"Team\"}");

            // The user creates it where suggested, and the browser fills it and adds it to the dashboard.
            final DocRef visualisation = create(VisualisationDoc.TYPE, "Messages per sender", create.getFolder());
            visualisationStore.writeDocument(visualisationStore.readDocument(visualisation).copy()
                    .functionName(create.getFunctionName())
                    .scriptRef(create.getScriptRef())
                    .settings(create.getSettings())
                    .build());

            // The dashboard searches, as it does with the vis component linked to the table.
            final VisResult result = dashboardVisResult(table, visualisation, create.getComponentSettings());

            final Map<String, Object> data = readMap(result.getJsonData());
            final List<?> rows = (List<?>) data.get("values");
            assertThat(rows).hasSize(1);
            // category, value, series: the order the adapter reads them in.
            assertThat(rows.getFirst()).isEqualTo(List.of("alice", 3.0, "Sales"));
            assertThat((List<?>) data.get("types")).isEqualTo(List.of("TEXT", "NUMBER", "TEXT"));
        });
    }

    @Test
    void aGeneratedChartIsReplacedByRewritingItsVisualisation() {
        final UserRef admin = administrator();
        final UserRef viewer = user("viewer", Set.of());
        final TableSettings table = TableSettings.builder()
                .addColumns(Column.builder().id("c1").name("Sender").expression("'alice'")
                        .format(Format.TEXT).build())
                .addColumns(Column.builder().id("c2").name("Count").expression("3")
                        .format(Format.NUMBER).build())
                .addMaxResults(1000L)
                .build();
        final DocRef[] made = new DocRef[2];
        asUser(admin, () -> {
            final ExplorerNode libraries = folder("Visualisations", "ECharts");
            final DocRef echarts = create(ScriptDoc.TYPE, "ECharts", libraries);
            scriptStore.writeDocument(scriptStore.readDocument(echarts).copy().data(ECHARTS).build());
            final ExplorerNode reports = folder("Reports");
            final DocRef dashboard = create(DashboardDoc.TYPE, "Chats", reports);

            // A bar chart Stroom AI generated earlier, in pane 'Messages chart'.
            final VisualisationPlan bars = new VisualisationPlan("bar",
                    Map.of("category", "Sender", "value", "Count"), Map.of(), null, "Messages", "table-1",
                    "Messages chart", VisualisationPlan.BELOW, "hash", null, null);
            final AiCreateVisualisation created = visualisationActions.prepare(dashboard, "Messages per sender",
                    bars.toJson());
            final DocRef visualisation = create(VisualisationDoc.TYPE, "Messages per sender", created.getFolder());
            visualisationStore.writeDocument(visualisationStore.readDocument(visualisation).copy()
                    .functionName(created.getFunctionName())
                    .scriptRef(created.getScriptRef())
                    .settings(created.getSettings())
                    .build());
            assertThat(chartLibraries.isGenerated(visualisation)).isTrue();
            assertThat(chartLibraries.isGenerated(echarts)).isFalse();
            made[0] = dashboard;
            made[1] = visualisation;
            documentPermissionService.setPermission(echarts, viewer, DocumentPermission.USE);
            documentPermissionService.setPermission(visualisation, viewer, DocumentPermission.VIEW);
        });
        final DocRef dashboard = made[0];
        final DocRef visualisation = made[1];

        // The user chooses to replace it with a pie chart, renamed.
        final VisualisationPlan pie = new VisualisationPlan("pie", Map.of("name", "Sender", "value", "Count"),
                Map.of(), null, "Messages", "table-1", "Messages chart", null, "hash", null, null,
                new Replace("vis-1", "Messages chart", visualisation));

        // Someone who can't edit it can't replace it.
        assertThatThrownBy(() -> asUser(viewer, () -> {
            visualisationActions.prepare(dashboard, "Share of messages", pie.toJson());
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You don't have Edit permission on Visualisation 'Messages per sender', so it can't "
                            + "be replaced.");

        asUser(admin, () -> {
            final AiCreateVisualisation replaced = visualisationActions.prepare(dashboard, "Share of messages",
                    pie.toJson());

            // Rewritten and renamed, in the explorer too, in place: the same document.
            assertThat(replaced.getReplaced().getUuid()).isEqualTo(visualisation.getUuid());
            assertThat(replaced.getReplaced().getName()).isEqualTo("Share of messages");
            assertThat(replaced.getReplaceComponentId()).isEqualTo("vis-1");
            assertThat(replaced.getFolder()).isNull();
            // The pane keeps its name.
            assertThat(replaced.getComponentName()).isNull();
            assertThat(explorerNodeService.getNode(visualisation).orElseThrow().getName())
                    .isEqualTo("Share of messages");
            final VisualisationDoc doc = visualisationStore.readDocument(visualisation);
            assertThat(doc.getName()).isEqualTo("Share of messages");
            assertThat(doc.getSettings()).contains("{\"id\": \"kind\", \"type\": \"text\", \"label\": \"Chart "
                                                   + "Kind\", \"defaultValue\": \"pie\"}");
            assertThat(doc.getDescription()).startsWith("Generated by Stroom AI: a pie chart of table 'Messages'");
            assertThat(chartLibraries.isGenerated(visualisation)).isTrue();

            // The pane's settings, merged as the browser merges them (an empty value removes one), draw the pie.
            final Map<String, Object> merged = new LinkedHashMap<>(readMap(
                    VisualisationActions.componentSettings(ChartKind.BAR, Map.of("category", "Sender",
                            "value", "Count"))));
            readMap(replaced.getComponentSettings()).forEach((key, value) -> {
                if ("".equals(value)) {
                    merged.remove(key);
                } else {
                    merged.put(key, value);
                }
            });
            assertThat(merged).isEqualTo(Map.of("name", "Sender", "value", "Count"));
            final VisResult result = dashboardVisResult(table, visualisation, JsonUtil.writeValueAsString(merged,
                    false));
            final List<?> rows = (List<?>) readMap(result.getJsonData()).get("values");
            assertThat(rows.getFirst()).isEqualTo(List.of("alice", 3.0));
        });
    }

    private VisResult dashboardVisResult(final TableSettings table,
                                         final DocRef visualisation,
                                         final String componentSettings) {
        final DashboardSearchRequest request = DashboardSearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.API).build())
                .search(Search.builder()
                        .dataSourceRef(new DocRef("Dual", "Dual", "Dual"))
                        .expression(ExpressionOperator.builder().build())
                        .incremental(false)
                        .build())
                .componentResultRequests(List.of(
                        TableResultRequest.builder()
                                .componentId("table-1")
                                .tableSettings(table)
                                .requestedRange(new OffsetRange(0L, 100L))
                                .fetch(Fetch.ALL)
                                .build(),
                        VisResultRequest.builder()
                                .componentId("vis-1")
                                .visualisation(visualisation)
                                .json(componentSettings)
                                .tableSettings(table)
                                .requestedRange(new OffsetRange(0L, 1000L))
                                .fetch(Fetch.ALL)
                                .build()))
                .dateTimeSettings(DateTimeSettings.builder().build())
                .timeout(30_000L)
                .storeHistory(false)
                .build();
        DashboardSearchResponse response = dashboardService.search(request);
        final long deadline = System.currentTimeMillis() + 30_000;
        while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()
               && System.currentTimeMillis() < deadline) {
            response = dashboardService.search(request.copy().queryKey(response.getQueryKey()).build());
        }
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).as("errors").isNullOrEmpty();
        final List<Result> results = NullSafe.list(response.getResults());
        return results.stream()
                .filter(VisResult.class::isInstance)
                .map(VisResult.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No visualisation result in " + results));
    }

    private QLVisResult visResult(final String query) {
        final QuerySearchRequest request = QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.API).build())
                .query(query)
                .queryContext(QueryContext.builder().dateTimeSettings(DateTimeSettings.builder().build()).build())
                .incremental(false)
                .timeout(30_000L)
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, 100L))
                .build();
        DashboardSearchResponse response = queryService.search(request);
        final long deadline = System.currentTimeMillis() + 30_000;
        while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()
               && System.currentTimeMillis() < deadline) {
            response = queryService.search(request.copy().queryKey(response.getQueryKey()).build());
        }
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).as("errors").isNullOrEmpty();
        final List<Result> results = NullSafe.list(response.getResults());
        return results.stream()
                .filter(QLVisResult.class::isInstance)
                .map(QLVisResult.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No visualisation result in " + results));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(final String json) {
        return JsonUtil.readValue(json, Map.class);
    }

    private ExplorerNode folder(final String... names) {
        return explorerService.ensureFolderPath(DocPath.fromParts(names), explorerNodeService.getRoot(),
                PermissionInheritance.DESTINATION);
    }

    private DocRef create(final String type, final String name, final ExplorerNode folder) {
        return explorerService.create(type, name, folder, PermissionInheritance.DESTINATION).getDocRef();
    }
}
