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

package stroom.mcp.impl.tools;

import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What the plan reads about a build from Stroom, where it is worked out here: a pipeline's open slots, what the
/// survey docs say together, and JSON as the Stroom MCP server writes it.
class TestStroomBuildFacts {

    @Test
    void theSlotsAChildMustFillAreItsParsersConverterAndItsFirstXslt() {
        final BuildWorld world = new BuildWorld();
        PipelineLayers.given(world.pipelineService, "p", PipelineLayers.of(PipelineLayers.layer("p")
                .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter", "decorationFilter", "XSLTFilter",
                        "writer", "XMLWriter")));
        // A second XSLT step (decoration) is optional.
        assertThat(StroomBuildFacts.openSlots(world.pipelineShapes.merged("p")))
                .containsExactly("dsParser.textConverter", "translationFilter.xslt");
        PipelineLayers.given(world.pipelineService, "q", PipelineLayers.of(PipelineLayers.layer("q")
                .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter")
                .property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, "tc", "TC"))
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x", "X"))));
        assertThat(StroomBuildFacts.openSlots(world.pipelineShapes.merged("q"))).isEmpty();
    }

    @Test
    void surveysSayTogetherWhetherTheFeedIsCovered() {
        final Map<String, Object> one = state(true, "json lines", 5, 3);
        final Map<String, Object> two = state(false, null, 6, 2);
        final Map<String, Object> survey = StroomBuildFacts.combine(List.of(one, two)).orElseThrow();
        assertThat(survey.get("saturated")).isEqualTo(false);
        assertThat(survey.get("format")).isEqualTo("json lines");
        assertThat((List<?>) survey.get("locations")).hasSize(5);
        assertThat(survey.get("streams")).isEqualTo(List.of(5L, 6L));
        assertThat(String.valueOf(survey.get("sample")).lines()).hasSize(5);
        // More locations than a call can be given: left out, and stepped over the streams instead.
        assertThat(StroomBuildFacts.combine(List.of(state(true, null, 5, 61))).orElseThrow().get("locations"))
                .isNull();
        assertThat(StroomBuildFacts.combine(List.of())).isEmpty();
    }

    @Test
    void aSurveysStateIsReadFromItsFence() {
        assertThat(StroomBuildFacts.readState("# ACME - Survey\n\n```json survey-state\n{\"saturated\": true}\n```\n"))
                .contains(Map.of("saturated", true));
        assertThat(StroomBuildFacts.readState("no state")).isEmpty();
        assertThat(StroomBuildFacts.readState("```json survey-state\n[1]\n```")).isEmpty();
    }

    @Test
    void jsonIsWrittenAsThePythonServerWritesIt() {
        // json.dumps(['create_feed', {'feed name': 'A', 'n': [1, True, None]}], sort_keys=True)
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("n", Arrays.asList(1, true, null));
        details.put("feed name", "A");
        assertThat(PythonJson.dumps(List.of("create_feed", details)))
                .isEqualTo("[\"create_feed\", {\"feed name\": \"A\", \"n\": [1, true, null]}]");
        // json.dumps({'copy_of': 'prod-é', 'verified': []}, indent=1, sort_keys=True)
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("verified", List.of());
        entry.put("copy_of", "prod-é");
        assertThat(PythonJson.dumps(entry, 1)).isEqualTo("{\n \"copy_of\": \"prod-\\u00e9\",\n \"verified\": []\n}");
    }

    private static Map<String, Object> state(final boolean saturated,
                                             final String format,
                                             final int stream,
                                             final int examples) {
        final List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < examples; i++) {
            list.add(Map.of("text", "{\"n\": " + i + "}", "location", Map.of("stream", stream, "part", 0,
                    "record", i)));
        }
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("saturated", saturated);
        state.put("format", format);
        state.put("shapes", Map.of("a", Map.of("examples", list)));
        return state;
    }
}
