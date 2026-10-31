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
import stroom.ai.impl.DashboardSettings.Setting;
import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// The dashboard components and settings the agent can change: components read from the dashboard's description,
/// and each setting's value checked before it is offered.
class TestDashboardSettings {

    private static final String DASHBOARD = """
            Time range: All time
            Shown components: Query, Table

            ## Query 'Query' (id query-1)
            Data source: ElasticIndex 'TWITTER-ECS-v1' (uuid 6cacb72d)
            Expression:
            AND
            Settings: queryOnOpen false; autoRefresh false; refreshInterval null

            ## Table 'Table' (id table-1)
            Query: query-1

            ## Text 'Mike's preview' (id text-1)
            """;

    @Test
    void componentsAreReadFromTheDashboardsDescription() {
        final List<Component> components = DashboardComponents.parse(DASHBOARD);

        assertThat(components).containsExactly(
                new Component("Query", "Query", "query-1", new DocRef("ElasticIndex", "6cacb72d", "TWITTER-ECS-v1")),
                new Component("Table", "Table", "table-1", null),
                new Component("Text", "Mike's preview", "text-1", null));
        assertThat(DashboardComponents.find(components, "table")).map(Component::id).contains("table-1");
        assertThat(DashboardComponents.find(components, "Nope")).isEmpty();
        assertThat(DashboardComponents.describe(components))
                .isEqualTo("Query 'Query', Table 'Table', Text 'Mike's preview'");
    }

    @Test
    void anEmbeddedQueryHasItsRunSettings() {
        assertThat(DashboardSettings.keys(DashboardComponents.EMBEDDED_QUERY))
                .containsExactly("queryOnOpen", "autoRefresh", "refreshInterval");
        assertThat(DashboardSettings.normalise(setting(DashboardComponents.EMBEDDED_QUERY, "refreshInterval"), "5m"))
                .isEqualTo("5m");
    }

    @Test
    void eachKindOfValueIsChecked() {
        assertThat(DashboardSettings.normalise(setting("Table", "showDetail"), "Yes")).isEqualTo("true");
        assertThat(DashboardSettings.normalise(setting("Table", "showDetail"), "off")).isEqualTo("false");
        assertThat(DashboardSettings.normalise(setting("Table", "pageSize"), " 50 ")).isEqualTo("50");
        assertThat(DashboardSettings.normalise(setting("Table", "maxResults"), "1000, 10")).isEqualTo("1000,10");
        assertThat(DashboardSettings.normalise(setting("Query", "refreshInterval"), "10m")).isEqualTo("10m");

        assertThatThrownBy(() -> DashboardSettings.normalise(setting("Table", "showDetail"), "maybe"))
                .hasMessage("showDetail takes true or false.");
        assertThatThrownBy(() -> DashboardSettings.normalise(setting("Table", "pageSize"), "0"))
                .hasMessageContaining("whole number from 1");
        assertThatThrownBy(() -> DashboardSettings.normalise(setting("Table", "maxResults"), "10,x"))
                .hasMessageContaining("whole number");
        assertThatThrownBy(() -> DashboardSettings.normalise(setting("Query", "refreshInterval"), "soon"))
                .hasMessageContaining("duration such as 30s");
        assertThatThrownBy(() -> DashboardSettings.normalise(setting("Query", "dataSource"), " "))
                .hasMessage("dataSource needs a value.");
    }

    @Test
    void settingsBelongToTheirComponentType() {
        assertThat(DashboardSettings.find("Table", "queryOnOpen")).isEmpty();
        assertThat(DashboardSettings.keys("Text")).containsExactly("table", "pipeline", "showAsHtml",
                "showStepping");
        assertThat(DashboardSettings.keys("Embedded Query")).containsExactly("queryOnOpen", "autoRefresh",
                "refreshInterval");
        // A key two component types share means the same for both, as a change's label and value are found by key
        // alone.
        assertThat(DashboardSettings.SETTINGS.stream()
                .collect(Collectors.groupingBy(Setting::key))
                .values())
                .allSatisfy(same -> assertThat(same).extracting(Setting::kind, Setting::label)
                        .containsOnly(tuple(same.getFirst().kind(),
                                same.getFirst().label())));
    }

    private static Setting setting(final String type, final String key) {
        return DashboardSettings.find(type, key).orElseThrow();
    }
}
