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

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiPipelineStagesConfig {

    @Test
    void testTheDefaultsAreStroomsStandardTemplatesAndMarkers() {
        final AiPipelineStagesConfig stages = new AiConfig().getPipelineStages();
        assertThat(stages.getTranslation().getTemplateFolders()).containsExactly("System/Template Pipelines");
        assertThat(stages.getTranslation().getTemplateNames()).containsExactly("Event Data (*)");
        assertThat(stages.getTranslation().getSchemaGroups()).containsExactly("EVENTS");
        assertThat(stages.getTranslation().getStreamTypes()).containsExactly("Events");
        assertThat(stages.getIndexing().getTemplateFolders())
                .containsExactly("System/Template Pipelines", "System/Template Pipelines/Elasticsearch");
        assertThat(stages.getIndexing().getTemplateNames())
                .containsExactly("Indexing", "Events to Elasticsearch", "Indexing - Elasticsearch");
        assertThat(stages.getDiscovery().getTemplateNames()).containsExactly("Raw to Elasticsearch");
        assertThat(stages.getReference().getTemplateNames()).containsExactly("Reference Data");
        assertThat(stages.getReference().getSchemaGroups()).containsExactly("REFERENCE_DATA");
        assertThat(stages.getRecords().getStreamTypes()).containsExactly("Records");
        assertThat(stages.getForwarding().getTemplateFolders()).isEmpty();
        // Markers are tested in this order.
        assertThat(stages.byName().keySet())
                .containsExactly("translation", "reference", "records", "indexing", "discovery", "forwarding");
    }

    @Test
    void testAPropertyLeftOutKeepsItsStagesDefault() {
        final AiPipelineStagesConfig stages = JsonUtil.readValue(
                "{\"translation\": {\"schemaGroups\": [\"ACME-EVENTS\"]}, \"reference\": {\"templateNames\": []}}",
                AiPipelineStagesConfig.class);
        assertThat(stages.getTranslation().getSchemaGroups()).containsExactly("ACME-EVENTS");
        assertThat(stages.getTranslation().getStreamTypes()).containsExactly("Events");
        assertThat(stages.getTranslation().getTemplateFolders()).containsExactly("System/Template Pipelines");
        // Given empty: any name.
        assertThat(stages.getReference().getTemplateNames()).isEmpty();
        assertThat(stages.getReference().getSchemaGroups()).containsExactly("REFERENCE_DATA");
        assertThat(stages.getIndexing()).isEqualTo(new AiPipelineStagesConfig().getIndexing());
    }

    @Test
    void testTheConfigReadsBackAsItWasWritten() {
        final AiPipelineStagesConfig stages = new AiPipelineStagesConfig(
                new AiPipelineStageConfig(List.of("System/Acme/Templates"), List.of("Acme *"), null, null),
                null, null, null, null, null);
        final AiPipelineStagesConfig read = JsonUtil.readValue(JsonUtil.writeValueAsString(stages),
                AiPipelineStagesConfig.class);
        assertThat(read).isEqualTo(stages);
        assertThat(read.getTranslation().getSchemaGroups()).containsExactly("EVENTS");
    }
}
