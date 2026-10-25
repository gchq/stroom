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

import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

class TestIndexValues {

    private static final FieldPlan LUCENE = plan(FieldPlan.LUCENE);
    private static final FieldPlan ELASTIC = plan(FieldPlan.ELASTICSEARCH);
    private static final List<String> FIELDS = List.of("UserName", "SourceIp", "Count", "Message");

    private final IndexDocs indexDocs = Mockito.mock(IndexDocs.class);
    private final IndexSearches searches = Mockito.mock(IndexSearches.class);
    private final IndexValues values = new IndexValues(indexDocs, searches);
    private final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
    private final ToolCallContext context = Mockito.mock(ToolCallContext.class);

    @Test
    void theFieldsAWildcardFindsAValueOfAreEachBackendsOwn() {
        // Lucene keeps keyword, ip and boolean fields as terms, an analysed text field not; Elasticsearch finds
        // nothing for a wildcard on an ip field. Neither takes one on a number.
        assertThat(IndexValues.wildcarded(FieldPlan.LUCENE, LUCENE, FIELDS)).containsExactly("UserName", "SourceIp");
        assertThat(IndexValues.wildcarded(FieldPlan.ELASTICSEARCH, ELASTIC, FIELDS))
                .containsExactly("UserName", "Message");
        final ExpressionOperator any = IndexValues.anyOf(List.of("UserName", "SourceIp"));
        assertThat(any.op()).isEqualTo(ExpressionOperator.Op.OR);
        assertThat(any.getChildren()).extracting(item -> ((ExpressionTerm) item).getValue()).containsOnly("*");
    }

    @Test
    void theFieldsNotInTheSampleAreThoseNoSampledEventHas() {
        assertThat(IndexValues.notInSample(LUCENE, Map.of("EventSource/User/Id", 100.0,
                "EventDetail/Authenticate/Outcome/Description", 0.0)))
                .containsExactly("SourceIp", "Count", "Message");
    }

    @Test
    void aLuceneIndexIsSearchedOnceForTheWildcardedFieldsAndOnceForTheRest() {
        // Asked for by the user: what kind of data a field the sample has none of holds, on either backend.
        Mockito.when(indexDocs.written(merged)).thenReturn(List.of(new IndexDocs.Index(
                new DocRef(LuceneIndexDoc.TYPE, "i1", "ACME"), null, null)));
        Mockito.when(searches.search(any(), any(), anyInt(), anyString(), any(Duration.class)))
                .thenReturn(new IndexSearches.Found(List.of(Map.of("UserName", "alice", "SourceIp", "10.0.0.1"),
                        Map.of("UserName", "alice"), Map.of("UserName", "bob"), Map.of("UserName", "carol"),
                        Map.of("UserName", "dave")), List.of(), true))
                .thenReturn(new IndexSearches.Found(List.of(Map.of("Count", "3"), Map.of("Message", "")),
                        List.of(), true));

        final Map<String, List<String>> found = values.examples(context, merged, LUCENE, FIELDS);

        // At most three, each once; a field with none (an empty value) left out.
        assertThat(found).containsEntry("UserName", List.of("alice", "bob", "carol"))
                .containsEntry("SourceIp", List.of("10.0.0.1"))
                .containsEntry("Count", List.of("3"))
                .doesNotContainKey("Message");
        final ArgumentCaptor<IndexSearches.Probe> probes = ArgumentCaptor.forClass(IndexSearches.Probe.class);
        Mockito.verify(searches, Mockito.times(2)).search(any(), probes.capture(), anyInt(), anyString(),
                any(Duration.class));
        assertThat(probes.getAllValues().getFirst().expression().op()).isEqualTo(ExpressionOperator.Op.OR);
        assertThat(probes.getAllValues().getLast().expression().getChildren()).isNullOrEmpty();
    }

    @Test
    void anElasticsearchIndexIsSearchedForTheDocumentsHoldingAnyOfThem() {
        Mockito.when(indexDocs.written(merged)).thenReturn(List.of(new IndexDocs.Index(
                new DocRef(ElasticIndexDoc.TYPE, "e1", "ACME"), "acme-v1", null)));
        Mockito.when(searches.search(any(), any(), anyInt(), anyString(), any(Duration.class)))
                .thenReturn(new IndexSearches.Found(List.of(Map.of("Message", "Logon failed")), List.of(), true));

        assertThat(values.examples(context, merged, ELASTIC, List.of("Message")))
                .containsEntry("Message", List.of("Logon failed"));
        Mockito.verify(searches, Mockito.times(1)).search(any(), any(), anyInt(), anyString(), any(Duration.class));
    }

    @Test
    void nothingIsGivenWhereTheIndexCantBeSearched() {
        Mockito.when(indexDocs.written(merged)).thenReturn(List.of());
        assertThat(values.examples(context, merged, LUCENE, FIELDS)).isEmpty();
        Mockito.when(indexDocs.written(merged)).thenThrow(new IllegalStateException("gone"));
        assertThat(values.examples(context, merged, LUCENE, FIELDS)).isEmpty();
        assertThat(values.examples(context, merged, LUCENE, List.of())).isEmpty();
    }

    private static FieldPlan plan(final String backend) {
        return FieldPlan.of(backend, "acme-v1", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("UserName", "keyword", "EventSource/User/Id"),
                PlannedField.of("SourceIp", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("Count", "long", "EventDetail/Authenticate/Outcome/Reason"),
                PlannedField.of("Message", "text", "EventDetail/Authenticate/Outcome/Description")));
    }
}
