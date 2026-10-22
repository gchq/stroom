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

import stroom.ai.impl.AiConfig;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineService;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/// What processing with a pipeline writes, read from its elements (the Stroom MCP server's `output_stream_type`,
/// `_writes_output`, `_sends_to_kafka` and `_is_indexing`).
class TestPipelineOutputs {

    private final PipelineService pipelineService = PipelineLayers.service();
    private final PipelineOutputs outputs = new PipelineOutputs(PipelineLayers.shapes(pipelineService,
            new AiConfig()));

    @Test
    void aTranslationPipelineWritesTheStreamTypeItsAppenderSays() {
        PipelineLayers.given(pipelineService, "p", PipelineLayers.of(PipelineLayers.layer("p")
                .chain("xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender")
                .property("streamAppender", "streamType", "Reference")));

        assertThat(outputs.writesOutput("p")).isTrue();
        assertThat(outputs.outputStreamType("p")).isEqualTo("Reference");
        assertThat(outputs.sendsToKafka("p")).isFalse();
        assertThat(outputs.isIndexing("p")).isFalse();
    }

    @Test
    void anAppenderThatDoesntSayWritesEvents() {
        PipelineLayers.given(pipelineService, "p", PipelineLayers.of(PipelineLayers.layer("p")
                .chain("xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender")));

        assertThat(outputs.outputStreamType("p")).isEqualTo("Events");
    }

    @Test
    void anIndexingOrKafkaPipelineWritesNoOutputStream() {
        // Seen in Stroom: an Events pipeline that writes only on error, and an indexing pipeline, each waited out
        // two timeouts.
        PipelineLayers.given(pipelineService, "i", PipelineLayers.of(PipelineLayers.layer("i")
                .chain("xsltFilter", "XSLTFilter", "indexingFilter", "ElasticIndexingFilter",
                        "streamAppender", "StreamAppender")));
        PipelineLayers.given(pipelineService, "k", PipelineLayers.of(PipelineLayers.layer("k")
                .chain("xsltFilter", "XSLTFilter", "kafka", "StandardKafkaProducer")));

        assertThat(outputs.writesOutput("i")).isFalse();
        assertThat(outputs.isIndexing("i")).isTrue();
        assertThat(outputs.writesOutput("k")).isFalse();
        assertThat(outputs.sendsToKafka("k")).isTrue();
    }

    @Test
    void aPipelineThatCantBeReadIsTakenToWriteEvents() {
        Mockito.when(pipelineService.fetchPipelineLayers(any(DocRef.class)))
                .thenThrow(new RuntimeException("no such pipeline"));

        assertThat(outputs.writesOutput("x")).isTrue();
        assertThat(outputs.outputStreamType("x")).isEqualTo("Events");
        assertThat(outputs.sendsToKafka("x")).isFalse();
        assertThatThrownBy(() -> outputs.isIndexing("x")).isInstanceOf(PipelineUnreadableException.class);
    }
}
