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

import stroom.ai.impl.tools.MergedPipeline.ElementProperty;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What an indexing pipeline's documentation says of document ids and reprocessing, from its indexing filter.
class TestIndexReprocessing {

    @Test
    void anElasticsearchPipelinePurgesOnReprocessByDefault() {
        // Seen in chat 177: the documentation claimed an _id of <StreamId>-<EventId>, so re-indexing replaced a
        // stream's documents. Stroom assigns no id, and only a reprocess filter deletes the stream's documents.
        final String note = IndexReprocessing.note(Map.of("xsltFilter", "XSLTFilter",
                "elasticIndexingFilter", "ElasticIndexingFilter"), Map.of());

        assertThat(note).contains("bulk `create` with no id, so Elasticsearch assigns it")
                .contains("no document id is derived from the event")
                .contains("A reprocess processor filter first deletes the index's documents of each stream")
                .contains("`purgeOnReprocess` is true")
                .contains("outside a reprocess filter is indexed again beside its earlier documents")
                .doesNotContain("StreamId-");
    }

    @Test
    void anElasticsearchPipelineThatDoesNotPurgeSaysSo() {
        final String note = IndexReprocessing.note(Map.of("elasticIndexingFilter", "ElasticIndexingFilter"),
                Map.of(new ElementProperty("elasticIndexingFilter", "purgeOnReprocess"), false));

        assertThat(note).contains("`purgeOnReprocess` is false, so reprocessing a stream deletes nothing")
                .doesNotContain("first deletes");
    }

    @Test
    void aLucenePipelineAddsAndDeletesNone() {
        final String note = IndexReprocessing.note(Map.of("indexingFilter", "IndexingFilter"), Map.of());

        assertThat(note).contains("Lucene indexing adds each event's document")
                .contains("deletes none, whether the stream is reprocessed or not");
    }

    @Test
    void aPipelineWithNoIndexingFilterSaysNothing() {
        assertThat(IndexReprocessing.note(Map.of("xsltFilter", "XSLTFilter"), Map.of())).isEmpty();
        assertThat(IndexReprocessing.note(null)).isEmpty();
    }
}
