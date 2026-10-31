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
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// evaluate_pipeline's `index` section: the fields an indexing pipeline's stepped documents have against its index's.
class TestIndexEvaluation {

    private static final PipelineDoc PIPELINE = PipelineDoc.builder().uuid("p").name("ACME-Index").build();
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "i", "ACME");
    private static final String DOCUMENT = "<records xmlns=\"records:2\"><record>"
                                           + "<data name=\"StreamId\" value=\"7\"/>"
                                           + "<data name=\"EventId\" value=\"1\"/>"
                                           + "<data name=\"UserId\" value=\"John Smith\"/>"
                                           + "<data name=\"Port\" value=\"http\"/>"
                                           + "<data name=\"Agent\" value=\"curl\"/>"
                                           + "</record></records>";

    private final IndexDocs indexDocs = Mockito.mock(IndexDocs.class);
    private final IndexTemplateAgreements agreements = Mockito.mock(IndexTemplateAgreements.class);
    private final IndexEvaluation evaluation = new IndexEvaluation(indexDocs, agreements);

    TestIndexEvaluation() {
        Mockito.when(indexDocs.written(any())).thenReturn(List.of(new IndexDocs.Index(LUCENE, null, null)));
        Mockito.when(indexDocs.fields(any())).thenReturn(List.of(
                field("StreamId", "id", null),
                field("EventId", "id", null),
                field("UserId", "text", "KEYWORD"),
                field("Port", "integer", null),
                field("Unused", "text", "ALPHA_NUMERIC")));
    }

    @Test
    void fieldsWrittenAreComparedWithTheIndexsMissingUnusedAndMismatched() {
        final Map<String, Object> index = evaluation.index(null, PIPELINE, List.of(DOCUMENT), null);

        assertThat(index).containsEntry("status", "checked")
                .containsEntry("backend", "lucene")
                .containsEntry("index_fields", 5)
                .containsEntry("documents_stepped", 1)
                .containsEntry("fields_written", 5);
        assertThat(index.get("index")).isEqualTo(Map.of("type", LuceneIndexDoc.TYPE, "uuid", "i", "name", "ACME"));
        // Written but not in the index: not searchable.
        assertThat(index.get("written_not_in_index")).isEqualTo(List.of("Agent"));
        assertThat((String) index.get("hint")).contains("Lucene indexes only the fields its index doc lists");
        // In the index, written by nothing.
        assertThat(index.get("index_fields_not_written")).isEqualTo(List.of("Unused"));
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> mismatches = (List<Map<String, Object>>) index.get("type_mismatches");
        assertThat(mismatches).extracting(m -> m.get("field")).containsExactly("UserId", "Port");
        // A keyword analyzer on free text: a word search finds nothing.
        assertThat(mismatches.getFirst()).containsEntry("index_type", "text (KEYWORD analyzer)")
                .containsEntry("example", "John Smith");
        assertThat((String) mismatches.getFirst().get("problem")).contains("keyword field");
        assertThat(mismatches.get(1)).containsEntry("index_type", "integer").containsEntry("values_not_fitting", 1);
        assertThat((String) mismatches.get(1).get("problem")).contains("not a whole number");
    }

    @Test
    void whatCantBeReadIsSaidAndTheRestGiven() {
        // Not stepped (no Stepping permission): the index and its fields, but nothing to compare them with.
        final Map<String, Object> notStepped = evaluation.index(null, PIPELINE, null, "You don't have permission "
                                                                                    + "to step pipelines");
        assertThat(notStepped).containsEntry("status", "partial").containsEntry("index_fields", 5)
                .doesNotContainKey("written_not_in_index");
        assertThat(notStepped.get("notes").toString()).contains("You don't have permission to step pipelines");

        // The index's fields not readable.
        Mockito.when(indexDocs.fields(any())).thenThrow(new IllegalStateException("No permission to read index"));
        final Map<String, Object> noFields = evaluation.index(null, PIPELINE, List.of(DOCUMENT), null);
        assertThat(noFields).containsEntry("status", "partial").containsEntry("fields_written", 5);
        assertThat(noFields.get("notes").toString()).contains("No permission to read index");

        // No index doc the user may view.
        Mockito.when(indexDocs.written(any())).thenReturn(List.of());
        assertThat(evaluation.index(null, PIPELINE, List.of(DOCUMENT), null)).containsEntry("status", "no_index");
        Mockito.when(indexDocs.written(any())).thenThrow(new IllegalStateException("denied"));
        assertThat(evaluation.index(null, PIPELINE, List.of(DOCUMENT), null)).containsEntry("status", "unavailable")
                .containsEntry("reason", "The index docs it writes to can't be read: denied");
    }

    @Test
    void anElasticsearchIndexSaysWhereItWritesAndPointsToAnAgreedTemplate() {
        final DocRef elastic = new DocRef(ElasticIndexDoc.TYPE, "e", "ACME ES");
        Mockito.when(indexDocs.written(any())).thenReturn(List.of(new IndexDocs.Index(elastic, "acme-v1",
                ElasticIndexDoc.builder().uuid("e").name("ACME ES").build())));
        final Map<String, Object> keyword = new LinkedHashMap<>(field("UserId", "keyword", null));
        keyword.put("elasticsearch_type", "keyword");
        Mockito.when(indexDocs.fields(any())).thenReturn(List.of(keyword));
        final Map<String, Object> destination = Map.of("index name", "acme-v1", "cluster", "Main");
        Mockito.when(agreements.elasticDestination("p")).thenReturn(Optional.of(destination));
        Mockito.when(agreements.agreementProblem(PIPELINE, destination)).thenReturn(Optional.empty());

        final Map<String, Object> index = evaluation.index(null, PIPELINE, List.of(DOCUMENT), null);

        assertThat(index).containsEntry("backend", "elasticsearch").containsEntry("cluster", "Main")
                .containsEntry("index_name", "acme-v1");
        @SuppressWarnings("unchecked")
        final Map<String, Object> template = (Map<String, Object>) index.get("index_template");
        assertThat(template).containsEntry("agreed", true);
        assertThat((String) template.get("next")).contains("check_index_template pipeline_uuid=p")
                .contains("don't run it");
        assertThat((String) index.get("hint")).contains("Elasticsearch adds a field");
    }

    private static Map<String, Object> field(final String name, final String type, final String analyzer) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("type", type);
        if (analyzer != null) {
            field.put("analyzer", analyzer);
        }
        return field;
    }
}
