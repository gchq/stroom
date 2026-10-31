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
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.processor.shared.ProcessorFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// What indexing a build's sample needs of its index: when it is dropped first, and the batch size while the
/// pipeline is developed.
class TestIndexingRuns {

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void anIndexElasticsearchRefusedEveryWriteToIsNotDropped(final String marker, final boolean dropped) {
        // Seen in testing: a 403, the index dropped first all the same, and the account, unable to create it,
        // refused again.
        final ProcessorFilter filter = w.idFilter("ix", 3, 6);
        w.output(6, 60, "Error", "ix", 3);
        w.errorTexts.put(60L, "[1:1 elasticIndexingFilter] ERROR: " + marker + "\n");
        final PipelineDoc pipeline = PipelineDoc.builder().uuid("ix").name("IX").build();
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("index name", "ecs-acme-v1");
        destination.put("cluster", "ES");

        final Map<String, String> details = w.indexingRuns.freshIndex(pipeline, destination, null, List.of(filter))
                .details();

        assertThat(details.containsKey("already indexed")).isEqualTo(dropped);
        if (dropped) {
            assertThat(details.get("already indexed")).contains("auto_configure or create_index");
        } else {
            assertThat(details.get("earlier runs")).contains("nothing is dropped").contains("Approve only once");
        }
    }

    @Test
    void anIndexIsDroppedAsItsAgreedTemplateMakesIt() {
        assertThat(IndexingRuns.dropRequest(doc(ProcessingWorld.agreed("PUT _index_template/x\n{\"data_stream\": {}}")),
                "x")).isEqualTo("DELETE _data_stream/x");
        assertThat(IndexingRuns.dropRequest(doc(ProcessingWorld.agreed("PUT _index_template/x\n{}")), "x"))
                .isEqualTo("DELETE x");
        assertThat(IndexingRuns.dropRequest(PipelineDoc.builder().uuid("p").build(), "x"))
                .isEqualTo("DELETE _data_stream/x (a data stream), or DELETE x (an index)");
    }

    @Test
    void theBuildsElasticsearchPipelineIndexesInSmallBatchesWhileItIsDeveloped() {
        w.pipeline(ProcessingWorld.P1, true, null);
        w.elastic("p1");

        assertThat(w.indexingRuns.developmentBatch("p1", true)).isTrue();
        assertThat(w.indexingRuns.developmentBatch("p1", true)).isFalse();
        final List<PipelineProperty> set = PipelineDocs.addedProperties(w.pipelineDoc("p1"));
        assertThat(set).extracting(property -> property.getElement() + "." + property.getName() + "="
                                               + property.getValue().getInteger())
                .containsExactly("elasticIndexingFilter.batchSize=10");
        assertThat(w.indexingRuns.developmentBatch("p1", false)).isTrue();
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1"))).isEmpty();
        assertThat(w.indexingRuns.developmentBatch("p1", false)).isFalse();
    }

    @Test
    void aProductionPipelinesBatchSizeIsLeftAlone() {
        w.pipeline(ProcessingWorld.P1, false, null, WriteGuard.GENERATED);
        w.elastic("p1");
        w.pipeline(new DocRef("Pipeline", "t1", "Acme-Events"), true, null);
        w.translation("t1");

        assertThat(w.indexingRuns.developmentBatch("p1", true)).isFalse();
        assertThat(w.indexingRuns.developmentBatch("t1", true)).isFalse();
        assertThat(PipelineDocs.addedProperties(w.pipelineDoc("p1"))).isEmpty();
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of("Elasticsearch rejected document 1 of 1 in a bulk request (security_exception): action "
                             + "[indices:admin/auto_create] is unauthorized for user [stroom]", false),
                Arguments.of("Elasticsearch rejected document 1 of 1 in a bulk request (index_not_found_exception): "
                             + "no such index", false),
                Arguments.of("Elasticsearch rejected document 2 of 3 in a bulk request (document_parsing_exception): "
                             + "failed to parse", true));
    }

    private static PipelineDoc doc(final Map<String, Object> agreed) {
        return PipelineDoc.builder().uuid("p").description(ProcessingWorld.withAgreed(agreed)).build();
    }
}
