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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineFeeds {

    private final ProcessorFilterAccess processorFilterAccess = Mockito.mock(ProcessorFilterAccess.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final PipelineFeeds pipelineFeeds = new PipelineFeeds(processorFilterAccess, documentAccess,
            streamReader);
    private final Map<String, List<ProcessorFilter>> filters = new HashMap<>();

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.findDocRef(Mockito.anyString())).thenAnswer(invocation ->
                Optional.of(new DocRef(PipelineDoc.TYPE, invocation.getArgument(0), "P")));
        Mockito.when(processorFilterAccess.filtersOf(Mockito.any())).thenAnswer(invocation -> {
            final DocRef pipeline = invocation.getArgument(0);
            return new ProcessorFilterAccess.Filters(filters.getOrDefault(pipeline.getUuid(), List.of()), null);
        });
        Mockito.when(documentAccess.read(Mockito.any(DocRef.class), Mockito.eq(DictionaryDoc.class)))
                .thenReturn(Optional.of(DictionaryDoc.builder().uuid("d1").name("Firewall feeds")
                        .data("FORTIGATE-V1\nPaloAlto-V2\n").build()));
    }

    @Test
    void aPipelinesFeedsAreReadFromItsFiltersDictionariesAndUpstreamPipeline() {
        // Seen in testing: asked which feeds a pipeline acts on, the agent found its filters unreadable (a
        // dictionary of feeds), and fell back on a keyword search for documents.
        filters.put("events", List.of(
                filter(1, true, term("Feed", Condition.IN_DICTIONARY, null,
                                new DocRef(DictionaryDoc.TYPE, "d1", "Firewall feeds")),
                        term("Type", Condition.EQUALS, "Raw Events", null)),
                filter(2, false, term("Feed", Condition.IS_DOC_REF, null,
                        new DocRef("Feed", "f9", "ACME-VPN-V1.0")))));
        filters.put("index", List.of(filter(3, true, term("Type", Condition.EQUALS, "Events", null),
                term("Pipeline", Condition.IS_DOC_REF, null, new DocRef(PipelineDoc.TYPE, "events", "FW-Events")))));

        final Map<String, Object> read = pipelineFeeds.pipelineFeeds("index");

        assertThat(read.get("feeds")).isEqualTo(List.of("ACME-VPN-V1.0", "FORTIGATE-V1", "PaloAlto-V2"));
        assertThat(read.get("reads_output_of")).isEqualTo(List.of("FW-Events"));
        assertThat(outputOf(read).getFirst().get("its_feeds"))
                .isEqualTo(List.of("ACME-VPN-V1.0", "FORTIGATE-V1", "PaloAlto-V2"));
        final Map<String, Object> events = pipelineFeeds.pipelineFeeds("events");
        assertThat(entries(events).get(0).get("feeds_from_dictionaries")).isEqualTo(List.of("Firewall feeds"));
        assertThat(entries(events).get(0).get("stream_types")).isEqualTo(List.of("Raw Events"));
        assertThat(entries(events).get(1).get("feeds")).isEqualTo(List.of("ACME-VPN-V1.0"));
    }

    @Test
    void notAllowedToListThemIsSaidNotAFailureOfTheWholeDescription() {
        Mockito.doReturn(new ProcessorFilterAccess.Filters(List.of(),
                        "You need the Manage Processors permission to list them"))
                .when(processorFilterAccess).filtersOf(Mockito.any());

        assertThat((String) pipelineFeeds.pipelineFeeds("p").get("filters_unreadable"))
                .contains("Stroom refused to list this pipeline's processor filters")
                .contains("Manage Processors");
    }

    @Test
    void aPipelineWithNoFiltersProcessesNothingOnItsOwn() {
        assertThat((String) pipelineFeeds.pipelineFeeds("template").get("note"))
                .startsWith("No processor filters");
    }

    @Test
    void aFeedNamedByReferenceShowsItsName() {
        // Asked for by the user: Feed IS_DOC_REF, not EQUALS a name, so Stroom keeps the filter as a dependency of
        // the feed.
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTerm(term("Feed", Condition.IS_DOC_REF, null, new DocRef("Feed", "feed-uuid", "ACME")))
                .build();

        assertThat(PipelineFeeds.terms(expression)).containsExactly("Feed IS_DOC_REF ACME");
    }

    @Test
    void severalFeedsBetweenCommasAndASampleStreamsFeedAreRead() {
        Mockito.when(streamReader.meta(42)).thenReturn(Meta.builder().id(42).feedName("SAMPLE-FEED").build());

        assertThat(pipelineFeeds.filterFeeds(List.of(term("Feed", Condition.EQUALS, "A, B", null)), new HashMap<>(),
                true)).containsExactly("a", "b");
        // A filter on a sample stream names no feed: the stream's is the one read.
        assertThat(pipelineFeeds.filterFeeds(List.of(term("Id", Condition.EQUALS, "42", null)), new HashMap<>(),
                false)).containsExactly("SAMPLE-FEED");
    }

    @Test
    void aDictionaryImportingItselfIsReadOnce() {
        Mockito.doReturn(Optional.of(DictionaryDoc.builder().uuid("d1").name("Loop").data("A")
                        .imports(List.of(new DocRef(DictionaryDoc.TYPE, "d1", "Loop"))).build()))
                .when(documentAccess).read(Mockito.any(DocRef.class), Mockito.eq(DictionaryDoc.class));

        assertThat(pipelineFeeds.filterFeeds(List.of(term("Feed", Condition.IN_DICTIONARY, null,
                new DocRef(DictionaryDoc.TYPE, "d1", "Loop"))), new HashMap<>(), false)).containsExactly("A");
        Mockito.verify(documentAccess, Mockito.times(1)).read(Mockito.any(DocRef.class),
                Mockito.eq(DictionaryDoc.class));
    }

    static ProcessorFilter filter(final int id, final boolean enabled, final ExpressionTerm... terms) {
        final ExpressionOperator.Builder expression = ExpressionOperator.builder().op(Op.AND);
        for (final ExpressionTerm term : terms) {
            expression.addTerm(term);
        }
        return ProcessorFilter.builder()
                .id(id)
                .enabled(enabled)
                .queryData(QueryData.builder().expression(expression.build()).build())
                .build();
    }

    static ExpressionTerm term(final String field, final Condition condition, final String value, final DocRef docRef) {
        return ExpressionTerm.builder().field(field).condition(condition).value(value).docRef(docRef).build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(final Map<String, Object> read) {
        return (List<Map<String, Object>>) read.get("filters");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> outputOf(final Map<String, Object> read) {
        return (List<Map<String, Object>>) entries(read).getFirst().get("output_of");
    }
}
