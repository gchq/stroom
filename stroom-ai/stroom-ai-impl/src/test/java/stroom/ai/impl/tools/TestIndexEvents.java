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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.IndexEvents.Find;
import stroom.ai.impl.tools.IndexEvents.Term;
import stroom.ai.impl.tools.IndexSearches.Counted;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.query.api.Column;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// search_events with an index: an event the user describes, found in an index through Stroom (the Stroom MCP
/// server's find_events, asked for by the user: one rare event among many indexed streams, which sampling would
/// miss). Ported from its `tests/test_find_events.py`.
class TestIndexEvents {

    private static final DocRef WEB = new DocRef(ElasticIndexDoc.TYPE, "i", "web");
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "l", "events");

    private final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final IndexSearches indexSearches = Mockito.mock(IndexSearches.class);
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final DataSourceProvider provider = Mockito.mock(DataSourceProvider.class);
    private final ToolCallContext context = ToolCalls.inChat(1);
    private IndexEvents indexEvents;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(registry.findDataSourceByUuid(Mockito.anyString())).thenAnswer(invocation ->
                List.of(WEB, LUCENE).stream()
                        .filter(docRef -> docRef.getUuid().equals(invocation.getArgument(0)))
                        .findFirst());
        Mockito.when(registry.getDataSourceDocRefs()).thenReturn(List.of(WEB, LUCENE));
        Mockito.when(registry.getDataSourceProvider(Mockito.anyString())).thenReturn(Optional.of(provider));
        Mockito.when(provider.getTimeField(Mockito.any())).thenReturn(Optional.of(QueryField.createDate("@timestamp")));
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.USE)))
                .thenReturn(true);
        Mockito.when(securityContext.useAsReadResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        Mockito.when(documentAccess.read(Mockito.any(), Mockito.eq(ElasticIndexDoc.class)))
                .thenReturn(Optional.empty());
        indexEvents = new IndexEvents(new DataSourceAccess(() -> registry, securityContext), () -> queryService,
                documentAccess, indexSearches, () -> metaService);
    }

    @Test
    void onlyFieldsTheIndexHasWithTheOneMeantNamed() {
        fields(QueryField.createLong("StreamId"), QueryField.createLong("EventId"), QueryField.createDate("@timestamp"),
                QueryField.createKeyword("user.name"), QueryField.createIpV4Address("source.ip"));

        assertThatThrownBy(() -> find(List.of(new Term("user.nmae", null, "alice")), null, false))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageStartingWith("Not fields of index 'web': user.nmae (like user.name)")
                .hasMessageContaining(". Its fields: ");
        // A search Stroom answers wrongly is refused, as verify_index refuses it.
        assertThatThrownBy(() -> find(List.of(new Term("source.ip", null, "10.0.*")), null, false))
                .hasMessageContaining("CIDR")
                .hasMessageContaining("use EQUALS '10.0.0.0/16' (a CIDR range)");
        assertThatThrownBy(() -> find(List.of(), null, false))
                .hasMessageContaining("doesn't list the index");
        Mockito.verifyNoInteractions(indexSearches);
    }

    @Test
    void hitsComeWithTheirStreamAndEventAndWhereTheRestAre() {
        fields(QueryField.createKeyword("StreamId"), QueryField.createKeyword("EventId"),
                QueryField.createKeyword("@timestamp"), QueryField.createKeyword("user.name"));
        final Map<String, String> row = Map.of("StreamId", "7", "EventId", "3",
                "@timestamp", "2026-10-13T00:00:00.000Z", "user.name", "alice");
        final List<String> doings = searchesFind(List.of(row), List.of(Map.of("StreamId", "7", "events", "2")));
        Mockito.when(indexSearches.count(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(new Counted(2L, List.of(), true));

        final Map<String, Object> found = find(List.of(new Term("user.name", null, "alice")), "day()-7d", false);

        assertThat(found.get("total")).isEqualTo(2L);
        assertThat(found.get("hits")).isEqualTo(List.of(row));
        assertThat(found.get("by_stream")).isEqualTo(List.of(Map.of("stream_id", "7", "events", "2")));
        assertThat(found.get("searched"))
                .isEqualTo(List.of("user.name EQUALS alice", "@timestamp BETWEEN day()-7d,day()+1d"));
        assertThat((String) found.get("next")).contains("locate_event stream_id=<its StreamId> event_id=<its EventId>");
        assertThat(found).doesNotContainKeys("affected", "errors");
        // Each search told to the client as it runs (asked for by the user).
        assertThat(doings).containsExactly(
                "Searching web for user.name EQUALS alice AND @timestamp BETWEEN day()-7d,day()+1d",
                "Grouping the matching events in web by stream");
    }

    @Test
    void theHitsAreNewestFirstWithTheIdsTheTimeAndTheFieldsSearched() {
        fields(QueryField.createKeyword("StreamId"), QueryField.createKeyword("EventId"),
                QueryField.createDate("@timestamp"), QueryField.createKeyword("user.name"),
                QueryField.createKeyword("host.name"));
        final List<Probe> probes = new ArrayList<>();
        Mockito.when(indexSearches.search(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    probes.add(invocation.getArgument(1));
                    return new Found(List.of(), List.of(), true);
                });
        Mockito.when(indexSearches.count(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(new Counted(0L, List.of(), true));

        final Map<String, Object> found = indexEvents.find(context, new Find("i",
                List.of(new Term("user.name", null, "alice")), null, null, List.of("host.name"), 5, false));

        final Probe hits = probes.getFirst();
        assertThat(hits.columns()).extracting(Column::getName)
                .containsExactly("StreamId", "EventId", "@timestamp", "user.name", "host.name");
        assertThat(hits.columns().get(2).getSort()).isNotNull();
        assertThat(hits.maxResults()).isEqualTo(5);
        assertThat(hits.expression().getChildren()).hasSize(1);
        assertThat((String) found.get("next")).startsWith("Nothing matches.");
        assertThat(found.get("total")).isEqualTo(0L);
    }

    @Test
    void isNullAndIsNotNullAreSearchedAsNoValueAndAnyValueOnElasticsearch() {
        fields(QueryField.createKeyword("StreamId"), QueryField.createKeyword("EventId"),
                QueryField.createKeyword("tags"));
        final List<Probe> probes = new ArrayList<>();
        Mockito.when(indexSearches.search(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    probes.add(invocation.getArgument(1));
                    return new Found(List.of(), List.of(), true);
                });
        Mockito.when(indexSearches.count(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(new Counted(null, List.of("Too slow"), false));

        final Map<String, Object> found = find(List.of(new Term("tags", Condition.IS_NOT_NULL, "")), null, false);

        assertThat(found.get("searched")).isEqualTo(List.of("tags EQUALS *"));
        assertThat(((ExpressionTerm) probes.getFirst().expression().getChildren().getFirst()).getCondition())
                .isEqualTo(Condition.EQUALS);
        // No count: the hits are the total, and Stroom's error is given.
        assertThat(found.get("total")).isEqualTo(0L);
        assertThat(found.get("errors")).isEqualTo(List.of("Too slow"));
    }

    @Test
    void aFieldTheLuceneIndexDoesNotIndexIsRefusedSayingWhy() {
        fields(QueryField.createKeyword("StreamId"),
                QueryField.builder().fldName("Raw").fldType(FieldType.TEXT).queryable(false).build());

        assertThatThrownBy(() -> indexEvents.find(context, new Find("l", List.of(new Term("Raw", null, "x")),
                null, null, List.of(), 10, false)))
                .hasMessage(MisleadingSearches.REFUSAL + "Raw: the index doc's field isn't indexed, so no search "
                            + "through Stroom finds it (it is kept, and a dashboard column still shows it): leave "
                            + "it out of the checks");
    }

    @Test
    void aTimeRangeNeedsATimeField() {
        fields(QueryField.createKeyword("user.name"));
        Mockito.when(provider.getTimeField(Mockito.any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> find(List.of(), "day()-1d", false))
                .hasMessage("Index 'web' has no time field to search a time range on");
    }

    @Test
    void anIndexTheUserCannotUseIsOneThatDoesNotExist() {
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.USE)))
                .thenReturn(false);

        assertThatThrownBy(() -> find(List.of(new Term("user.name", null, "alice")), null, false))
                .hasMessage("No Elastic Index or Lucene Index doc with UUID or name 'i' that you can use");
        Mockito.verifyNoInteractions(queryService);
    }

    @Test
    void allStreamsListsWhatAChangeReaches() {
        Mockito.when(metaService.find(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                Meta.builder().id(8).parentDataId(6L).build(), Meta.builder().id(9).parentDataId(7L).build())));

        final Map<String, Object> affected = indexEvents.affected("ecs-web-v1", List.of(
                Map.of("stream_id", "8", "events", "5"), Map.of("stream_id", "9.0", "events", "1")), 6L);

        assertThat(affected.get("events_streams")).isEqualTo(List.of(8L, 9L));
        assertThat(affected.get("raw_streams")).isEqualTo(List.of(6L, 7L));
        assertThat(affected.get("events")).isEqualTo(6L);
        assertThat(affected.get("elasticsearch_delete"))
                .isEqualTo("POST ecs-web-v1/_delete_by_query {\"query\": {\"terms\": {\"StreamId\": [8, 9]}}}");
        assertThat((String) affected.get("delete_when")).contains("reprocessed");
        assertThat((String) affected.get("reprocess")).contains("user's to reprocess");
    }

    @Test
    void allStreamsOnAFindGivesWhatAChangeReachesAndTheTwentyWithTheMost() {
        fields(QueryField.createKeyword("StreamId"), QueryField.createKeyword("EventId"),
                QueryField.createKeyword("user.name"));
        final List<Map<String, String>> streams = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            streams.add(Map.of("StreamId", String.valueOf(i), "events", "1"));
        }
        searchesFind(List.of(Map.of("StreamId", "1", "EventId", "1", "user.name", "alice")), streams);
        Mockito.when(indexSearches.count(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .thenReturn(new Counted(25L, List.of(), true));
        Mockito.when(metaService.find(Mockito.any())).thenReturn(new ResultPage<>(List.of()));

        final Map<String, Object> found = find(List.of(new Term("user.name", null, "alice")), null, true);

        assertThat((List<?>) found.get("by_stream")).hasSize(IndexEvents.STREAMS);
        @SuppressWarnings("unchecked") final Map<String, Object> affected = (Map<String, Object>) found.get("affected");
        assertThat((List<?>) affected.get("events_streams")).hasSize(25);
        // A Lucene index, or an Elastic Index doc the user can't read: no delete request.
        assertThat(affected).doesNotContainKey("elasticsearch_delete");
        // The grouping asks for every stream, not only the twenty with the most.
        Mockito.verify(indexSearches).search(Mockito.any(), Mockito.argThat(probe -> probe != null
                && probe.maxResults() == IndexEvents.ALL_STREAMS), Mockito.eq(IndexEvents.ALL_STREAMS),
                Mockito.anyString());
    }

    private Map<String, Object> find(final List<Term> where, final String timeFrom, final boolean allStreams) {
        return indexEvents.find(context, new Find("i", where, timeFrom, null, List.of(), 10, allStreams));
    }

    private void fields(final QueryField... fields) {
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(fields)));
    }

    // The searches: the grouping by stream gives the streams, any other the hits. Returns what each said it did.
    private List<String> searchesFind(final List<Map<String, String>> hits,
                                      final List<Map<String, String>> streams) {
        final List<String> doings = new ArrayList<>();
        Mockito.when(indexSearches.search(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final Probe probe = invocation.getArgument(1);
                    doings.add(invocation.getArgument(3));
                    final boolean grouped = probe.columns().stream().anyMatch(column -> column.getGroup() != null
                                                                                        && column.getGroup() == 0);
                    return new Found(grouped
                            ? streams
                            : hits, List.of(), true);
                });
        return doings;
    }
}
