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

import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// update_pipeline, the tool.
class TestUpdatePipelineTool {

    private static final String XSLT_UUID = "5f0a2c1e-3b4d-4e6f-8a9b-0c1d2e3f4a5b";

    private final PipelineWorld pipelines = new PipelineWorld();
    private final UpdatePipelineTool tool = pipelines.updateTool();

    @Test
    void somethingToChangeMustBeGiven() {
        final ToolResult refused = call("{\"pipeline_uuid\": \"p\"}");

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).isEqualTo("Give set_properties, references, or both");
    }

    @Test
    void propertiesAreSetAndReferenceDataAttached() {
        pipelines.buildPipeline("p", "Acme-Events", null, PipelineLayers.layer("p").chain("dsParser", "DSParser",
                "translationFilter", "XSLTFilter"));
        pipelines.xslt(XSLT_UUID, "Acme-Events", null);
        pipelines.productionPipeline(PipelineWorld.LOADER, "Reference Loader", null,
                PipelineLayers.layer(PipelineWorld.LOADER));
        pipelines.feeds.feedAt("System/Feeds", "F", "f");
        Mockito.when(pipelines.referenceData.resolveLoader("F")).thenReturn(PipelineWorld.LOADER);

        final Map<String, Object> result = PipelineWorld.json(call("{\"pipeline_uuid\": \"p\", \"set_properties\": "
                                                                   + "\"translationFilter.xslt=" + XSLT_UUID
                                                                   + "\", \"references\": "
                                                                   + "[{\"feed\": \"F\"}]}"));

        assertThat(result).containsEntry("uuid", "p").containsEntry("name", "Acme-Events")
                .containsEntry("type", "Pipeline")
                .containsEntry("set", List.of("translationFilter.xslt"))
                .containsEntry("reference_data", List.of("F via Reference Loader on translationFilter"))
                .doesNotContainKey("note");
        assertThat(PipelineDocs.addedProperties(pipelines.pipelineDoc("p"))).containsExactly(new PipelineProperty(
                "translationFilter", "xslt", new PipelinePropertyValue(new DocRef("XSLT", XSLT_UUID, "Acme-Events"))));
    }

    @Test
    void aJsonParserNeverSetForTheArraySampleIsSetToo() {
        // A pipeline made before its XSLT (or with one written by hand) never had the JSON parser set for an array.
        pipelines.buildPipeline("p", "Acme-Events", null, PipelineLayers.layer("p").chain("jsonParser",
                "JSONParser", "translationFilter", "XSLTFilter"));
        pipelines.xslt("x1", "Acme-Events", Map.of("input", "json"));

        final Map<String, Object> result = PipelineWorld.json(call("{\"pipeline_uuid\": \"p\", \"set_properties\": "
                                                                   + "[{\"element\": \"translationFilter\", \"name\": "
                                                                   + "\"xslt\", \"doc_uuid\": \"x1\", \"doc_type\": "
                                                                   + "\"XSLT\"}]}"));

        assertThat(result).containsEntry("set", List.of("translationFilter.xslt", "jsonParser.addRootObject"));
        assertThat(String.valueOf(result.get("note"))).startsWith("Also set jsonParser.addRootObject false: the "
                                                                  + "build's sample is a JSON array");
        assertThat(PipelineDocs.addedProperties(pipelines.pipelineDoc("p"))).contains(new PipelineProperty(
                "jsonParser", "addRootObject", new PipelinePropertyValue(false)));
    }

    @Test
    void aPipelineOutsideTheBuildIsNotChanged() {
        pipelines.productionPipeline("prod", "Acme-Prod", null, PipelineLayers.layer("prod").chain("jsonParser",
                "JSONParser"));

        final ToolResult refused = call("{\"pipeline_uuid\": \"prod\", \"set_properties\": "
                                        + "\"jsonParser.addRootObject=false\"}");

        assertThat(refused.isError()).isTrue();
        assertThat(PipelineDocs.addedProperties(pipelines.pipelineDoc("prod"))).isEmpty();
    }

    private ToolResult call(final String arguments) {
        return tool.execute(BuildToolCalls.call(UpdatePipelineTool.NAME, arguments), BuildToolCalls.context());
    }
}
