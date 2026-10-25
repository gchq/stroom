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

import stroom.ai.impl.DashboardComponents.Component;
import stroom.ai.impl.DashboardComponents.EmbeddedQuery;
import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The components read from a dashboard's description, including its Visualisations, and the description without
/// their render reports.
class TestDashboardComponents {

    private static final String DASHBOARD = """
            Time range: All time
            Shown components: Messages, Connections

            ## Table 'Messages' (id table-1)
            Query: query-1

            ## Visualisation 'Connections' (id vis-1)
            Visualisation: 'Network graph' (uuid net-uuid)
            Linked table: 'Messages'
            Vis settings: {"source":"UserId","limit":10}
            Render report: {"rendered":true,"nodes":12}

            ## Visualisation 'Empty' (id vis-2)
            Visualisation: none
            Linked table: none
            Vis settings: not json
            """;

    @Test
    void visualisationsAreReadWithTheirDocumentTableAndSettings() {
        final List<Component> components = DashboardComponents.parse(DASHBOARD);

        assertThat(components).containsExactly(
                new Component("Table", "Messages", "table-1", null),
                new Component("Visualisation", "Connections", "vis-1", null,
                        new DocRef("Visualisation", "net-uuid", "Network graph"), "Messages",
                        Map.of("source", "UserId", "limit", "10")),
                new Component("Visualisation", "Empty", "vis-2", null));
        assertThat(components.get(1).settings()).containsExactly(Map.entry("source", "UserId"),
                Map.entry("limit", "10"));
    }

    @Test
    void visualisationLinesOnlyCountInAVisualisationsSection() {
        final List<Component> components = DashboardComponents.parse("""
                ## Text 'Preview' (id text-1)
                Visualisation: 'Network graph' (uuid net-uuid)
                Linked table: 'Messages'
                """);

        assertThat(components).containsExactly(new Component("Text", "Preview", "text-1", null));
    }

    @Test
    void renderReportsAreLeftOutWhenTellingWhetherTheDashboardHasChanged() {
        final String without = DashboardComponents.withoutRenderReports(DASHBOARD);

        assertThat(without).doesNotContain("Render report").contains("Vis settings: {\"source\":\"UserId\"");
        assertThat(without).isEqualTo(DASHBOARD.replace("Render report: {\"rendered\":true,\"nodes\":12}\n", ""));
        assertThat(DashboardComponents.withoutRenderReports("\nA\nB\n")).isEqualTo("\nA\nB\n");
        assertThat(DashboardComponents.withoutRenderReports(null)).isNull();
    }

    @Test
    void anEmbeddedQuerysStroomQlIsRead() {
        final String dashboard = """
                Time range: All time
                Shown components: Pairs

                ## Embedded Query 'Pairs' (id query-1)
                Query: embedded in the dashboard
                StroomQL:
                ```
                from "Chats"
                where event.code = "ChatMessage"

                select UserA, UserB
                ```
                Settings: queryOnOpen false; autoRefresh false; refreshInterval 10s

                ## Embedded Query 'Saved' (id query-2)
                Query: Query 'Chat pairs' (uuid q-uuid) by reference
                StroomQL:
                ```
                from "Chats" select UserA
                ```
                """;

        assertThat(DashboardComponents.embeddedQuery(dashboard, "query-1")).contains(new EmbeddedQuery(
                "from \"Chats\"\nwhere event.code = \"ChatMessage\"\n\nselect UserA, UserB", null));
        assertThat(DashboardComponents.embeddedQuery(dashboard, "query-2")).hasValueSatisfying(query -> {
            assertThat(query.query()).isEqualTo("from \"Chats\" select UserA");
            assertThat(query.isReference()).isTrue();
            assertThat(query.referenced()).isEqualTo(new DocRef("Query", "q-uuid", "Chat pairs"));
        });
        assertThat(DashboardComponents.parse(dashboard)).extracting(Component::type)
                .containsExactly(DashboardComponents.EMBEDDED_QUERY, DashboardComponents.EMBEDDED_QUERY);
    }

    @Test
    void anEmbeddedQueryWithoutItsStroomQlInFullIsntRead() {
        // No such component, or a table of that id.
        assertThat(DashboardComponents.embeddedQuery(DASHBOARD, "query-1")).isEmpty();
        assertThat(DashboardComponents.embeddedQuery(DASHBOARD, "table-1")).isEmpty();
        // Cut short before its closing fence.
        assertThat(DashboardComponents.embeddedQuery("## Embedded Query 'Pairs' (id query-1)\n"
                                                     + "Query: embedded in the dashboard\nStroomQL:\n```\n"
                                                     + "from \"Chats\"", "query-1")).isEmpty();
        assertThat(DashboardComponents.embeddedQuery(null, "query-1")).isEmpty();
    }
}
