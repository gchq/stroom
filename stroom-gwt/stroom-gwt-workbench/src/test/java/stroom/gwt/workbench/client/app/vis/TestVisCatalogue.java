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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVisCatalogue {

    // Bar needs Common and Grid; Grid needs Common, which needs D3
    private static final String JSON = JsonValues.toJson(Map.of(
            "visualisations", List.of(
                    file("Bar.Visualisation.v1.meta", "{\"type\": \"Visualisation\", \"uuid\": \"v1\", "
                                                      + "\"name\": \"Bar\", \"functionName\": \"vis.Bar\", "
                                                      + "\"scriptRef\": {\"type\": \"Script\", \"uuid\": \"bar\"}}",
                            "{\"tabs\": []}"),
                    // A second Bar, which the first wins over
                    file("Bar.Visualisation.v2.meta", "{\"uuid\": \"v2\", \"name\": \"Bar\"}", "{}"),
                    file("Pie.Visualisation.v3.meta", "{\"uuid\": \"v3\", \"name\": \"Pie\"}", null),
                    file("Bad.Visualisation.v4.meta", "{\"uuid\": \"v4\", \"name\": \"Bad\"}", "{nope")),
            "scripts", List.of(
                    file("Bar.Script.bar.meta", script("bar", "common", "grid"), null),
                    file("Grid.Script.grid.meta", script("grid", "common"), null),
                    file("Common.Script.common.meta", script("common", "d3"), null),
                    file("D3.Script.d3.meta", script("d3"), null),
                    file("Empty.Script.empty.meta", "", null))));

    @Test
    void testVisualisations() {
        final VisCatalogue catalogue = VisCatalogue.parse(JSON);
        assertThat(catalogue.getNames()).containsExactly("Bar", "Pie");
        assertThat(catalogue.has("Bar")).isTrue();
        assertThat(catalogue.has("Bad")).isFalse();
        assertThat(catalogue.uuid("Bar")).isEqualTo("v1");
        assertThat(catalogue.settings("Bar")).isEqualTo("{\"tabs\": []}");
        // The document is the .meta with its settings
        assertThat(JsonValues.parse(catalogue.visualisationDocJson("Bar"))).isEqualTo(JsonValues.parse(
                "{\"type\": \"Visualisation\", \"uuid\": \"v1\", \"name\": \"Bar\", \"functionName\": \"vis.Bar\", "
                + "\"scriptRef\": {\"type\": \"Script\", \"uuid\": \"bar\"}, \"settings\": \"{\\\"tabs\\\": []}\"}"));
    }

    @Test
    void testProblems() {
        assertThat(VisCatalogue.parse(JSON).getProblems()).containsExactly(
                "The settings of Bad.Visualisation.v4.meta aren't JSON: Expected a member name at position 1 "
                + "of JSON: {nope",
                "Empty.Script.empty.meta isn't a JSON object");
    }

    @Test
    void testIndexAsset() {
        final Map<String, Object> assetBased = file("Line.Visualisation.v5.meta",
                "{\"uuid\": \"v5\", \"name\": \"Line\"}", "{}");
        assetBased.put("indexAsset", true);
        final VisCatalogue catalogue = VisCatalogue.parse(JsonValues.toJson(Map.of(
                "visualisations", List.of(assetBased,
                        file("Bar.Visualisation.v1.meta", "{\"uuid\": \"v1\", \"name\": \"Bar\"}", "{}")),
                "scripts", List.of())));
        assertThat(catalogue.hasIndexAsset("Line")).isTrue();
        // Left out, as an older workbench server sends it
        assertThat(catalogue.hasIndexAsset("Bar")).isFalse();
        assertThatThrownBy(() -> catalogue.hasIndexAsset("Nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMissing() {
        final VisCatalogue catalogue = VisCatalogue.parse(JSON);
        assertThatThrownBy(() -> catalogue.uuid("Nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("There is no visualisation Nope, only [Bar, Pie]");
        assertThatThrownBy(() -> catalogue.settings("Pie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The visualisation Pie has no settings");
    }

    @Test
    void testLinkedScripts() {
        final VisCatalogue catalogue = VisCatalogue.parse(JSON);
        // Dependencies first, each once
        assertThat(names(catalogue.linkedScriptsJson("bar", List.of()))).containsExactly("d3", "common", "grid", "bar");
        // Leaving out those the frame has loaded
        assertThat(names(catalogue.linkedScriptsJson("bar", List.of("d3", "common")))).containsExactly("grid", "bar");
        assertThat(names(catalogue.linkedScriptsJson("nope", List.of()))).isEmpty();
    }

    @Test
    void testLinkedScriptsReply() {
        final String reply = VisCatalogue.parse(JSON).linkedScriptsReply(
                "{\"script\": {\"type\": \"Script\", \"uuid\": \"grid\"}, "
                + "\"loadedScripts\": [{\"type\": \"Script\", \"uuid\": \"d3\"}]}");
        assertThat(JsonValues.parse(reply)).isEqualTo(JsonValues.parse(
                "[{\"type\": \"Script\", \"uuid\": \"common\", \"name\": \"common\", \"version\": \"ver-common\"}, "
                + "{\"type\": \"Script\", \"uuid\": \"grid\", \"name\": \"grid\", \"version\": \"ver-grid\"}]"));
    }

    @Test
    void testCircularDependencies() {
        final VisCatalogue catalogue = VisCatalogue.parse(JsonValues.toJson(Map.of("scripts", List.of(
                file("A.Script.a.meta", script("a", "b"), null),
                file("B.Script.b.meta", script("b", "a"), null)))));
        assertThat(names(catalogue.linkedScriptsJson("a", List.of()))).containsExactly("b", "a");
    }

    @Test
    void testNotACatalogue() {
        assertThatThrownBy(() -> VisCatalogue.parse("[]")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> file(final String path, final String meta, final String settings) {
        final Map<String, Object> file = new java.util.LinkedHashMap<>();
        file.put("path", path);
        file.put("meta", meta);
        file.put("settings", settings);
        return file;
    }

    private static String script(final String uuid, final String... dependencies) {
        final StringBuilder sb = new StringBuilder("{\"type\": \"Script\", \"uuid\": \"" + uuid
                                                   + "\", \"name\": \"" + uuid + "\", \"version\": \"ver-" + uuid
                                                   + "\", \"dependencies\": [");
        for (int i = 0; i < dependencies.length; i++) {
            sb.append(i > 0
                    ? ", "
                    : "").append("{\"type\": \"Script\", \"uuid\": \"").append(dependencies[i]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static List<Object> names(final String json) {
        return ((List<?>) JsonValues.parse(json)).stream()
                .map(doc -> ((Map<?, ?>) doc).get("uuid"))
                .map(Object.class::cast)
                .toList();
    }
}
