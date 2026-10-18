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

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.util.shared.ResultPage;

import event.logging.Data;
import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestStreamMetaTool {

    private static final String OUT = "/outputs/stream_meta.csv";
    private static final long DAY_1 = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli();
    private static final long DAY_2 = Instant.parse("2026-10-02T11:00:00Z").toEpochMilli();

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final AttributeMapFactory attributeMapFactory = Mockito.mock(AttributeMapFactory.class);
    private final Store store = Mockito.mock(Store.class);
    private final List<MetaRow> rows = new ArrayList<>();
    private final Map<Long, Map<String, String>> storeAttributes = new HashMap<>();
    private final Map<Long, List<Map<String, String>>> storeParts = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        fixture = new ToolTestFixture(tempDir);
        fixture.add(new DocRef(FeedDoc.TYPE, "11111111-1111-1111-1111-111111111111", "WINDOWS-EVENTS"), null, true);
        fixture.add(new DocRef(FeedDoc.TYPE, "22222222-2222-2222-2222-222222222222", "SECRET-FEED"), null, false);
        fixture.add(new DocRef(PipelineDoc.TYPE, "33333333-3333-3333-3333-333333333333", "Windows pipeline"), null,
                true);
        rows.add(row(1, DAY_1, Map.of("Raw Size", "100", "Read Count", "5")));
        // The database's values for this stream have aged off.
        rows.add(row(2, DAY_1, Map.of()));
        rows.add(row(3, DAY_2, Map.of("Raw Size", "50")));
        storeAttributes.put(2L, Map.of("Raw Size", "200", "Feed", "WINDOWS-EVENTS"));
        storeParts.put(1L, List.of(Map.of("RemoteHost", "fw1"), Map.of("RemoteHost", "fw2")));
        storeParts.put(2L, List.of(Map.of("RemoteHost", "fw3")));
        storeParts.put(3L, List.of(Map.of("RemoteHost", "fw1", "Compression", "ZIP")));

        Mockito.when(metaService.findRows(Mockito.any())).thenAnswer(invocation -> new ResultPage<>(rows));
        Mockito.when(attributeMapFactory.getAttributes(Mockito.anyLong())).thenAnswer(invocation ->
                storeAttributes.getOrDefault(invocation.<Long>getArgument(0), Map.of()));
        Mockito.when(attributeMapFactory.getAttributeMapForPart(Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(invocation -> new AttributeMap(storeParts.get(invocation.<Long>getArgument(0))
                        .get(invocation.<Long>getArgument(1).intValue())));
        Mockito.when(store.openSource(Mockito.anyLong())).thenAnswer(invocation -> {
            final Source source = Mockito.mock(Source.class);
            Mockito.when(source.count()).thenReturn((long) storeParts.get(invocation.<Long>getArgument(0)).size());
            return source;
        });
    }

    @Test
    void valuesComeFromTheDatabaseAndElseTheStreamStore() {
        final String result = run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"attributes\":[\"Raw Size\"]}");

        assertThat(result)
                .contains("Found 3 stream(s)")
                .contains("Wrote 3 row(s) to " + OUT)
                .contains("Attribute values: 2 from the database, 1 from the stream store (1 stream(s) read).");
        assertThat(fixture.written.get(OUT)).isEqualTo("""
                Id,Feed,Type,Pipeline,Status,Create Time,Parent Id,Raw Size\r
                1,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-01T10:00:00.000Z,,100\r
                2,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-01T10:00:00.000Z,,200\r
                3,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-02T11:00:00.000Z,,50\r
                """);
        // Only the stream the database had nothing for was read.
        Mockito.verify(attributeMapFactory).getAttributes(2L);
        Mockito.verifyNoMoreInteractions(attributeMapFactory);
    }

    @Test
    void theSearchIsBuiltFromTheArguments() {
        run("{\"feeds\":[\"11111111-1111-1111-1111-111111111111\"],"
            + "\"pipeline\":\"33333333-3333-3333-3333-333333333333\",\"type\":\"Events\","
            + "\"from\":\"2026-10-01\",\"to\":\"2026-10-02T00:00:00Z\"}");

        final ArgumentCaptor<FindMetaCriteria> criteria = ArgumentCaptor.forClass(FindMetaCriteria.class);
        Mockito.verify(metaService).findRows(criteria.capture());
        assertThat(criteria.getValue().getExpression().toString())
                .contains("Feed = WINDOWS-EVENTS")
                .contains("Pipeline is Windows pipeline")
                .contains("Type = Events")
                .contains("Create Time >= 2026-10-01T00:00:00.000Z")
                .contains("Create Time < 2026-10-02T00:00:00.000Z")
                .contains("Status = Unlocked");
        assertThat(criteria.getValue().getPageRequest().getLength()).isEqualTo(StreamMetaTool.DEFAULT_LIMIT + 1);
    }

    @Test
    void eachPartHasTheHeadersItWasReceivedWith() {
        run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"parts\":true,\"attributes\":[\"RemoteHost\"]}");

        assertThat(fixture.written.get(OUT).lines())
                .containsExactly(
                        "Id,Feed,Type,Pipeline,Status,Create Time,Parent Id,Part,RemoteHost",
                        "1,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-01T10:00:00.000Z,,1,fw1",
                        "1,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-01T10:00:00.000Z,,2,fw2",
                        "2,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-01T10:00:00.000Z,,1,fw3",
                        "3,WINDOWS-EVENTS,Raw Events,,Unlocked,2026-10-02T11:00:00.000Z,,1,fw1");
    }

    @Test
    void groupingCountsAndSums() {
        final String result = run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"groupBy\":[\"day\"],"
                                  + "\"aggregates\":[\"count\",\"sum(Raw Size)\",\"max:Raw Size\"]}");

        assertThat(fixture.written.get(OUT).lines())
                .containsExactly(
                        "day,count,sum(Raw Size),max(Raw Size)",
                        "2026-10-01,2,300,200",
                        "2026-10-02,1,50,50");
        assertThat(result).contains("| 2026-10-01 | 2 | 300 | 200 |");
    }

    @Test
    void streamStoreReadsAreCappedWithANote() {
        rows.add(row(4, DAY_2, Map.of()));
        storeAttributes.put(4L, Map.of("Raw Size", "400"));
        fixture.setLimits(new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, null, 1));

        final String result = run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"attributes\":[\"Raw Size\"]}");

        assertThat(result)
                .contains("1 from the stream store (1 stream(s) read)")
                .contains("1 value(s) weren't read from the stream store because of the limit of 1 stream store "
                          + "reads per call");
        assertThat(fixture.written.get(OUT)).contains("4,WINDOWS-EVENTS,Raw Events,,Unlocked,"
                                                      + "2026-10-02T11:00:00.000Z,,\r\n");
    }

    @Test
    void withoutViewDataOnlyTheDatabasesValuesAreGiven() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);

        final String result = run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"attributes\":[\"Raw Size\"]}");

        assertThat(result)
                .contains("Attribute values: 2 from the database, 0 from the stream store (0 stream(s) read).")
                .contains("1 value(s) weren't read from the stream store because you don't have permission to "
                          + "view data");
        assertThat(fixture.written.get(OUT)).contains("2,WINDOWS-EVENTS,Raw Events,,Unlocked,"
                                                      + "2026-10-01T10:00:00.000Z,,\r\n");
        // The streams are still found: MetaService adds the user's feed permissions to the search itself.
        Mockito.verify(metaService).findRows(Mockito.any());
        Mockito.verifyNoInteractions(attributeMapFactory, store);
    }

    @Test
    void withoutViewDataPartsAreNotRead() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);

        final String result = run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"parts\":true,\"attributes\":[\"RemoteHost\"]}");

        assertThat(result).contains("so there is a row per stream instead");
        assertThat(fixture.written.get(OUT).lines()).hasSize(4);
        Mockito.verifyNoInteractions(attributeMapFactory, store);
    }

    @Test
    void aFailingViewDataCheckFailsClosed() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION))
                .thenThrow(new IllegalStateException("No user"));

        run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"attributes\":[\"Raw Size\"]}");

        Mockito.verifyNoInteractions(attributeMapFactory, store);
    }

    @Test
    void aFeedTheUserCantViewIsTreatedAsMissing() {
        final String hidden = message("{\"feeds\":[\"22222222-2222-2222-2222-222222222222\"]}");
        final String missing = message("{\"feeds\":[\"44444444-4444-4444-4444-444444444444\"]}");

        assertThat(hidden.replace("2222", "x")).isEqualTo(missing.replace("4444", "x"));
        assertThat(hidden).doesNotContain("SECRET");
        Mockito.verifyNoInteractions(metaService);
    }

    @Test
    void theSearchIsLoggedNamingTheStreams() {
        run("{\"feeds\":[\"WINDOWS-EVENTS\"],\"attributes\":[\"Raw Size\"]}");

        final List<SearchEventAction> events = fixture.logged(SearchEventAction.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getQuery().getRaw())
                .isEqualTo("feed is WINDOWS-EVENTS, status is Unlocked");
        assertThat(events.getFirst().getTotalResults()).hasToString("3");
        assertThat(events.getFirst().getData())
                .extracting(Data::getName, Data::getValue)
                .contains(tuple("StreamStoreReads", "1"));
    }

    @Test
    void badArgumentsAreRefused() {
        assertThat(message("{}")).contains("Give feeds, a pipeline, or both");
        assertThat(message("{\"feeds\":[\"X\"],\"groupBy\":[\"colour\"]}")).contains("Can't group by 'colour'");
        assertThat(message("{\"feeds\":[\"X\"],\"aggregates\":[\"avg(Raw Size)\"]}")).contains("isn't an aggregate");
        assertThat(message("{\"feeds\":[\"X\"],\"from\":\"last week\"}")).contains("isn't a time");
        assertThat(message("{\"feeds\":[\"X\"],\"status\":\"Gone\"}")).contains("Unlocked, Locked or Deleted");
    }

    @Test
    void periodsAreCountedBackFromNow() {
        final long now = System.currentTimeMillis();
        assertThat(StreamMetaTool.time("P1D", "from")).isBetween(now - 86_500_000L, now - 86_300_000L);
        assertThat(StreamMetaTool.time("PT1H", "from")).isBetween(now - 3_700_000L, now - 3_500_000L);
    }

    private String run(final String arguments) {
        return tool().run(ToolTestFixture.request(StreamMetaTool.NAME, arguments), 1);
    }

    private String message(final String arguments) {
        try {
            run(arguments);
            return "";
        } catch (final WorkspaceException e) {
            return e.getMessage();
        }
    }

    private StreamMetaTool tool() {
        return new StreamMetaTool(fixture.workspace, () -> metaService, () -> attributeMapFactory, () -> store,
                fixture.documentAccess(), fixture.auditLog());
    }

    private static MetaRow row(final long id, final long createMs, final Map<String, String> attributes) {
        final Meta meta = Meta.builder()
                .id(id)
                .feedName("WINDOWS-EVENTS")
                .typeName("Raw Events")
                .status(Status.UNLOCKED)
                .createMs(createMs)
                .build();
        return new MetaRow(meta, null, new HashMap<>(attributes));
    }
}
