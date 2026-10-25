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

import stroom.dashboard.impl.vis.VisSettings;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestEChartsTemplate {

    @Test
    void graphTemplateMapsColumnsAndStyle() {
        final String json = EChartsTemplate.settingsJson(ChartKind.GRAPH,
                Map.of("source", "Sender", "target", "Recipient"),
                Map.of("directed", "true"),
                "{\"color\":[\"#5470c6\"]}");

        final Map<?, ?> settings = JsonUtil.readValue(json, Map.class);
        final List<?> tabs = (List<?>) settings.get("tabs");
        assertThat(tabs.stream().map(tab -> String.valueOf(((Map<?, ?>) tab).get("name"))).toList())
                .containsExactly("Data", "Style", "Limits", "Advanced");

        final Map<String, Map<String, Object>> controls = controls(tabs);
        assertThat(controls.get("source")).containsEntry("type", "field").containsEntry("defaultValue", "Sender");
        assertThat(controls.get("target")).containsEntry("defaultValue", "Recipient");
        assertThat(controls.get("weight")).doesNotContainKey("defaultValue");
        assertThat(controls.get("directed")).containsEntry("type", "boolean").containsEntry("defaultValue", "true");
        assertThat(controls.get("maxNodes")).containsEntry("defaultValue", "300").containsEntry("min", "2");
        assertThat(controls.get("kind")).containsEntry("defaultValue", "graph");
        assertThat(controls.get("fields")).containsEntry("defaultValue",
                "source,target,weight,sourceGroup,targetGroup");
        // Edited in a code editor, under a label that names no charting library.
        assertThat(controls.get("options")).containsEntry("defaultValue", "{\"color\":[\"#5470c6\"]}")
                .containsEntry("type", "json")
                .containsEntry("label", "Chart Options (JSON)");
    }

    @Test
    void aChartGeneratedEarlierGetsTheCurrentOptionsControl() {
        // Generated with the options as a one-line text box: brought up to date, keeping what was set.
        final String earlier = EChartsTemplate.settingsJson(ChartKind.BAR, Map.of("category", "User"), Map.of(),
                        "{\"color\":[\"#5470c6\"]}")
                .replace("\"json\"", "\"text\"")
                .replace("Chart Options (JSON)", "ECharts Options (JSON)");

        final String updated = EChartsTemplate.withCurrentOptionsControl(earlier);

        final Map<String, Map<String, Object>> controls = controls(
                (List<?>) JsonUtil.readValue(updated, Map.class).get("tabs"));
        assertThat(controls.get("options")).containsEntry("type", "json")
                .containsEntry("label", "Chart Options (JSON)")
                .containsEntry("defaultValue", "{\"color\":[\"#5470c6\"]}");
        assertThat(controls.get("category")).containsEntry("defaultValue", "User");
        // Already current, or not settings at all: left as they are.
        assertThat(EChartsTemplate.withCurrentOptionsControl(updated)).isSameAs(updated);
        assertThat(EChartsTemplate.withCurrentOptionsControl("not json")).isEqualTo("not json");
    }

    @ParameterizedTest
    @EnumSource(ChartKind.class)
    void everyTemplateReadsAsVisSettingsWithEveryReferenceDeclared(final ChartKind kind) {
        final String json = EChartsTemplate.settingsJson(kind, Map.of(), Map.of(), null);

        // Stroom reads it as VisSettings to build the chart's table.
        final VisSettings visSettings = JsonUtil.readValue(json, VisSettings.class);
        assertThat(visSettings.getData().getStructure().getValues().getFields())
                .hasSize(kind.roles().size());
        assertThat(visSettings.getData().getStructure().getNest()).isNull();

        final Map<String, Map<String, Object>> controls = controls(
                (List<?>) JsonUtil.readValue(json, Map.class).get("tabs"));
        final Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(json);
        final List<String> references = new ArrayList<>();
        while (matcher.find()) {
            references.add(matcher.group(1));
        }
        assertThat(references).isNotEmpty().allSatisfy(reference -> assertThat(controls).containsKey(reference));
    }

    @Test
    void aThresholdIsATextControlOnTheStyleTab() {
        final Map<String, Object> settings = JsonUtil.readValue(EChartsTemplate.settingsJson(ChartKind.BAR,
                Map.of("category", "C", "value", "V"), Map.of("threshold", "100"), null), Map.class);
        final Map<String, Map<String, Object>> controls = controls((List<?>) settings.get("tabs"));

        assertThat(controls.get("threshold"))
                .containsEntry("type", "text")
                .containsEntry("defaultValue", "100");
        assertThat(controls.get("units")).containsEntry("defaultValue", "none");
        assertThat(((List<?>) settings.get("tabs")).stream()
                .map(tab -> (Map<?, ?>) tab)
                .filter(tab -> "Style".equals(tab.get("name")))
                .flatMap(tab -> ((List<?>) tab.get("controls")).stream())
                .map(control -> String.valueOf(((Map<?, ?>) control).get("id"))))
                .contains("threshold", "units", "logScale", "zoomSlider");
    }

    @Test
    void fieldOrderFollowsTheRoles() {
        assertThat(EChartsTemplate.fieldOrder(ChartKind.BAR)).isEqualTo("category,value,series");
        assertThat(EChartsTemplate.fieldOrder(ChartKind.SCATTER)).isEqualTo("x,y,size,series");
    }

    @Test
    void adapterReadsTheSameIdsAsTheTemplates() {
        final String adapter = new ChartLibraries(null, null, null, null, null, null).adapterSource();

        assertThat(adapter).contains("visualisations.EChartsGeneric = function");
        for (final ChartKind kind : ChartKind.values()) {
            assertThat(adapter).contains(kind.id() + ": " + kind.id() + "Model");
            for (final StyleSetting setting : kind.styleSettings()) {
                if (setting == StyleSetting.MAX_VALUES) {
                    // Only limits the rows Stroom gives the chart.
                    continue;
                }
                assertThat(adapter).as("setting %s of %s", setting.id(), kind).contains("\"" + setting.id() + "\"");
            }
            for (final ChartRole role : kind.roles()) {
                assertThat(adapter).as("role %s of %s", role.id(), kind).contains("\"" + role.id() + "\"");
            }
        }
        for (final String key : VisualisationPlan.OPTION_KEYS) {
            assertThat(adapter).contains("        " + key + ": true");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> controls(final List<?> tabs) {
        final Map<String, Map<String, Object>> controls = new LinkedHashMap<>();
        for (final Object tab : tabs) {
            for (final Object control : (List<?>) ((Map<?, ?>) tab).get("controls")) {
                controls.put((String) ((Map<?, ?>) control).get("id"), (Map<String, Object>) control);
            }
        }
        return controls;
    }
}
