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

package stroom.sqlstore.impl.pipeline;

import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.util.ProcessorUtil;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.impl.UpdatableTemporalStoreProvider;
import stroom.sqlstore.shared.ApplyChangesRequest;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.UnknownStoreException;
import stroom.util.shared.Severity;
import stroom.util.shared.TemporalEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSqlStoreFilter {

    @Mock
    private UpdatableTemporalStoreProvider storeProvider;

    @Mock
    private UpdatableTemporalStore updatableTemporalStore;

    /**
     * Mirrors {@code SqlStoreFilter.WRITE_BATCH_SIZE}, which is private.
     *
     * <p>If that constant changes and this one does not, the batch-boundary tests below stop
     * testing the boundary — they keep passing while silently exercising the under-threshold
     * path instead. {@link #testWriteBatchSizeStillMatchesTheFilter} guards against that.</p>
     */
    private static final int WRITE_BATCH_SIZE = 1_000;

    private List<Severity> loggedSeverities;
    private List<String> loggedMessages;
    private ErrorReceiverProxy errorReceiverProxy;

    @BeforeEach
    void setUp() {
        loggedSeverities = new ArrayList<>();
        loggedMessages = new ArrayList<>();

        final ErrorReceiver errorReceiver = new ErrorReceiver() {
            @Override
            public void log(final Severity severity, final stroom.util.shared.Location location,
                            final stroom.util.shared.ElementId elementId, final String message,
                            final stroom.util.shared.ErrorType errorType, final Throwable e) {
                loggedSeverities.add(severity);
                loggedMessages.add(message);
            }
        };
        errorReceiverProxy = new ErrorReceiverProxy(errorReceiver);
    }

    private void processXml(final String xml) {
        final MetaHolder metaHolder = new MetaHolder();
        final Meta meta = new Meta();
        metaHolder.setMeta(meta);

        final SqlStoreFilter sqlStoreFilter = new SqlStoreFilter(
                errorReceiverProxy,
                new LocationFactoryProxy(),
                metaHolder,
                storeProvider
        );

        final ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        ProcessorUtil.processXml(input, errorReceiverProxy, sqlStoreFilter, new LocationFactoryProxy());
    }

    @Test
    void testPlainValueParsing() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final String xml = """
                <referenceData xmlns="reference-data:2">
                    <reference>
                        <map>test-map</map>
                        <key>k1</key>
                        <value>simple-string-val</value>
                    </reference>
                </referenceData>
                """;

        processXml(xml);

        assertThat(loggedSeverities).isEmpty();

        final TemporalEntry captured = singleUpsertedEntry();
        assertThat(captured.getMap()).isEqualTo("test-map");
        assertThat(captured.getKey()).isEqualTo("k1");
        assertThat(captured.getValue()).isEqualTo("simple-string-val");

        // TODO Check effective date
    }

    @Test
    void testXmlValueParsing() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final String xml = """
                <referenceData xmlns="reference-data:2">
                    <reference>
                        <map>test-map</map>
                        <key>k1</key>
                        <value><Location><Country>UK</Country></Location></value>
                    </reference>
                </referenceData>
                """;

        processXml(xml);

        assertThat(loggedSeverities).isEmpty();

        final TemporalEntry captured = singleUpsertedEntry();
        assertThat(captured.getMap()).isEqualTo("test-map");
        assertThat(captured.getKey()).isEqualTo("k1");
        assertThat(captured.getValue()).contains("<Location><Country>UK</Country></Location>");
    }

    @Test
    void testCustomTimeHandling() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final String xml = """
                <referenceData xmlns="reference-data:2">
                    <reference>
                        <map>test-map</map>
                        <key>k1</key>
                        <time>2026-06-03T12:00:00.000Z</time>
                        <value>val</value>
                    </reference>
                </referenceData>
                """;

        processXml(xml);

        assertThat(loggedSeverities).isEmpty();

        final TemporalEntry captured = singleUpsertedEntry();
        assertThat(captured.getEffectiveTimeMs())
                .isEqualTo(Instant.parse("2026-06-03T12:00:00Z").toEpochMilli());
    }

    @Test
    void testUnknownStoreExceptionHandling() {
        Mockito.when(storeProvider.get("unknown-map"))
                .thenThrow(new UnknownStoreException("Unknown store: unknown-map"));

        final String xml = """
                <referenceData xmlns="reference-data:2">
                    <reference>
                        <map>unknown-map</map>
                        <key>k1</key>
                        <value>val</value>
                    </reference>
                </referenceData>
                """;

        processXml(xml);

        assertThat(loggedSeverities).contains(Severity.ERROR);
        assertThat(loggedMessages.getFirst()).contains("Unknown SQL store map 'unknown-map'");
    }

    @Test
    void testPlanBStateParsing() {
        final String xml = """
                <plan-b xmlns="plan-b:1" version="1.0">
                    <state>
                        <map>test-map</map>
                        <key>k1</key>
                        <value>plan-b-state-val</value>
                    </state>
                </plan-b>
                """;

        processXml(xml);

        assertThat(loggedSeverities).contains(Severity.ERROR);
        assertThat(loggedMessages.getFirst()).contains("SQL Store Filter can only process '<temporal-state>' "
                + "elements from the plan-b schema. Element '<state>' is not supported.");
        Mockito.verify(updatableTemporalStore, Mockito.never()).create(Mockito.any());
    }

    @Test
    void testPlanBTemporalStateParsing() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final String xml = """
                <plan-b xmlns="plan-b:1" version="1.0">
                    <temporal-state>
                        <map>test-map</map>
                        <key>k1</key>
                        <time>2026-06-03T15:30:00.000Z</time>
                        <value>plan-b-temporal-state-val</value>
                    </temporal-state>
                </plan-b>
                """;

        processXml(xml);

        assertThat(loggedSeverities).isEmpty();

        final TemporalEntry captured = singleUpsertedEntry();
        assertThat(captured.getMap()).isEqualTo("test-map");
        assertThat(captured.getKey()).isEqualTo("k1");
        assertThat(captured.getEffectiveTimeMs())
                .isEqualTo(Instant.parse("2026-06-03T15:30:00Z").toEpochMilli());
        assertThat(captured.getValue()).isEqualTo("plan-b-temporal-state-val");
    }

    @Test
    void testPlanBRangeStateParsing() {
        final String xml = """
                <plan-b xmlns="plan-b:1" version="1.0">
                    <range-state>
                        <map>test-map</map>
                        <range>
                            <from>1000</from>
                            <to>2000</to>
                        </range>
                        <value>plan-b-range-state-val</value>
                    </range-state>
                </plan-b>
                """;

        processXml(xml);

        assertThat(loggedSeverities).contains(Severity.ERROR);
        assertThat(loggedMessages.getFirst()).contains("SQL Store Filter can only process '<temporal-state>' "
                + "elements from the plan-b schema. Element '<range-state>' is not supported.");
        Mockito.verify(updatableTemporalStore, Mockito.never()).create(Mockito.any());
    }

    /**
     * Many entries must reach the store as one batched write, not one call each.
     *
     * <p>Regression test for the ingest cost: every entry used to go through
     * {@code UpdatableTemporalStore.create} on its own, each resolving and authorising the store,
     * writing an audit event and taking a connection. A stream with a hundred thousand entries paid
     * all of that a hundred thousand times.</p>
     */
    @Test
    void testEntriesAreWrittenAsOneBatch() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final StringBuilder xml = new StringBuilder("<referenceData>");
        final int entries = 250;
        for (int i = 0; i < entries; i++) {
            xml.append("<reference><map>test-map</map><key>k").append(i)
               .append("</key><value>v").append(i).append("</value></reference>");
        }
        xml.append("</referenceData>");

        processXml(xml.toString());

        assertThat(loggedSeverities).isEmpty();

        final ArgumentCaptor<ApplyChangesRequest> captor =
                ArgumentCaptor.forClass(ApplyChangesRequest.class);
        // One call, not one per entry.
        Mockito.verify(updatableTemporalStore, Mockito.times(1)).applyChanges(captor.capture());
        Mockito.verify(updatableTemporalStore, Mockito.never()).create(Mockito.any());

        final List<ChangeOperation> ops = captor.getValue().getOperations();
        assertThat(ops).hasSize(entries);
        // Order is preserved, so a later entry for the same key still wins.
        assertThat(ops.get(0).getEntry().getKey()).isEqualTo("k0");
        assertThat(ops.get(entries - 1).getEntry().getKey()).isEqualTo("k" + (entries - 1));
    }

    /**
     * The mirrored {@link #WRITE_BATCH_SIZE} must still equal the filter's own constant.
     *
     * <p>Without this, retuning the batch size in {@code SqlStoreFilter} would leave the boundary
     * tests green while they quietly stopped crossing a boundary at all. Reflection is the price
     * of keeping the production constant private.</p>
     */
    @Test
    void testWriteBatchSizeStillMatchesTheFilter() throws Exception {
        final java.lang.reflect.Field field =
                SqlStoreFilter.class.getDeclaredField("WRITE_BATCH_SIZE");
        field.setAccessible(true);
        assertThat(field.getInt(null))
                .as("TestSqlStoreFilter.WRITE_BATCH_SIZE mirrors SqlStoreFilter.WRITE_BATCH_SIZE; "
                    + "update the test constant to match")
                .isEqualTo(WRITE_BATCH_SIZE);
    }

    /**
     * Crossing the flush threshold must not lose, duplicate or reorder anything.
     *
     * <p>{@code testEntriesAreWrittenAsOneBatch} deliberately stays under the threshold, so it
     * proves only that entries are not written one at a time. This is the other half: once a
     * stream is long enough to flush mid-parse, the entries are split across several
     * {@code applyChanges} calls, and correctness now depends on the union of those calls rather
     * than on any single one.</p>
     *
     * <p>2,500 entries is two full batches and a partial one, so it exercises the mid-parse flush
     * at {@code pendingOps.size() >= WRITE_BATCH_SIZE} twice and the endProcessing flush once.</p>
     */
    @Test
    void testEntriesSpanningSeveralBatchesAreAllWrittenInOrder() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final int entries = (WRITE_BATCH_SIZE * 2) + 500;
        processXml(referenceDataXml("test-map", entries));

        assertThat(loggedSeverities).isEmpty();

        final ArgumentCaptor<ApplyChangesRequest> captor =
                ArgumentCaptor.forClass(ApplyChangesRequest.class);
        Mockito.verify(updatableTemporalStore, Mockito.times(3)).applyChanges(captor.capture());
        Mockito.verify(updatableTemporalStore, Mockito.never()).create(Mockito.any());

        // Full batches flush at exactly the threshold; the remainder goes out at endProcessing.
        assertThat(captor.getAllValues())
                .extracting(request -> request.getOperations().size())
                .containsExactly(WRITE_BATCH_SIZE, WRITE_BATCH_SIZE, 500);

        // Every entry, exactly once, still in document order across the batch boundaries.
        final List<String> keys = captor.getAllValues().stream()
                .flatMap(request -> request.getOperations().stream())
                .map(op -> op.getEntry().getKey())
                .toList();
        assertThat(keys).hasSize(entries);
        assertThat(keys).isEqualTo(expectedKeys(entries));
    }

    /**
     * A stream that lands exactly on the threshold must not emit a trailing empty batch.
     *
     * <p>The flush is triggered by {@code >=}, so entry number {@code WRITE_BATCH_SIZE} empties
     * the buffer before the parse ends. {@code endProcessing} then flushes again, and only the
     * {@code pendingOps.isEmpty()} guard in {@code flushPendingWrites} stops that second flush
     * running.</p>
     *
     * <p>Without the guard the failure is louder than a wasted transaction: the {@code finally}
     * block clears {@code pendingMapName} as well as {@code pendingOps}, so the second flush
     * resolves {@code storeProvider.get(null)} and reports an error against map {@code 'null'}.
     * Every stream whose entry count happened to be a multiple of the batch size would fail
     * visibly. Verified by removing the guard and watching this test fail on the logged
     * severity.</p>
     */
    @Test
    void testStreamEndingExactlyOnTheThresholdWritesOneBatch() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        processXml(referenceDataXml("test-map", WRITE_BATCH_SIZE));

        assertThat(loggedSeverities).isEmpty();

        final ArgumentCaptor<ApplyChangesRequest> captor =
                ArgumentCaptor.forClass(ApplyChangesRequest.class);
        Mockito.verify(updatableTemporalStore, Mockito.times(1)).applyChanges(captor.capture());
        assertThat(captor.getValue().getOperations()).hasSize(WRITE_BATCH_SIZE);
    }

    /**
     * The same holds at any multiple of the threshold, not just the first one.
     *
     * <p>{@code testStreamEndingExactlyOnTheThresholdWritesOneBatch} covers one batch exactly;
     * this covers two, because the claim being made is about every stream whose entry count is a
     * multiple of the batch size, and one example does not establish that.</p>
     */
    @Test
    void testStreamEndingOnASecondWholeBatchWritesNoEmptyBatch() {
        Mockito.when(storeProvider.get("test-map")).thenReturn(updatableTemporalStore);

        final int entries = WRITE_BATCH_SIZE * 2;
        processXml(referenceDataXml("test-map", entries));

        assertThat(loggedSeverities).isEmpty();

        final ArgumentCaptor<ApplyChangesRequest> captor =
                ArgumentCaptor.forClass(ApplyChangesRequest.class);
        Mockito.verify(updatableTemporalStore, Mockito.times(2)).applyChanges(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(request -> request.getOperations().size())
                .containsExactly(WRITE_BATCH_SIZE, WRITE_BATCH_SIZE);
    }

    /**
     * A change of map name flushes early, so batches follow the map, not just the count.
     *
     * <p>Each {@code applyChanges} goes to one store, so the buffer has to be flushed when the
     * map name changes even though it is nowhere near full. Interleaved maps therefore produce a
     * batch per run of entries, and an alternating stream defeats the batching entirely — which
     * is the behaviour, and worth pinning so it is noticed if someone tries to make the buffer
     * span maps.</p>
     */
    @Test
    void testChangingMapNameFlushesTheBufferEarly() {
        Mockito.when(storeProvider.get("map-a")).thenReturn(updatableTemporalStore);
        Mockito.when(storeProvider.get("map-b")).thenReturn(updatableTemporalStore);

        final StringBuilder xml = new StringBuilder("<referenceData>");
        for (int i = 0; i < 3; i++) {
            xml.append(reference("map-a", "a" + i, "v" + i));
            xml.append(reference("map-b", "b" + i, "v" + i));
        }
        xml.append("</referenceData>");

        processXml(xml.toString());

        assertThat(loggedSeverities).isEmpty();

        final ArgumentCaptor<ApplyChangesRequest> captor =
                ArgumentCaptor.forClass(ApplyChangesRequest.class);
        // Six runs of one entry each, not one batch of six.
        Mockito.verify(updatableTemporalStore, Mockito.times(6)).applyChanges(captor.capture());
        assertThat(captor.getAllValues())
                .flatExtracting(request -> request.getOperations())
                .extracting(op -> op.getEntry().getMap())
                .containsExactly("map-a", "map-b", "map-a", "map-b", "map-a", "map-b");
    }

    /**
     * Builds a reference-data document of {@code count} entries in one map, keyed {@code k0..kN}.
     *
     * @param mapName the map every entry targets; never null
     * @param count   how many entries to generate
     * @return the XML document; never null
     */
    private static String referenceDataXml(final String mapName, final int count) {
        final StringBuilder xml = new StringBuilder("<referenceData>");
        for (int i = 0; i < count; i++) {
            xml.append(reference(mapName, "k" + i, "v" + i));
        }
        return xml.append("</referenceData>").toString();
    }

    /** A single reference element. */
    private static String reference(final String mapName, final String key, final String value) {
        return "<reference><map>" + mapName + "</map><key>" + key
               + "</key><value>" + value + "</value></reference>";
    }

    /** The keys {@code referenceDataXml} generates, in order, for comparison against what arrived. */
    private static List<String> expectedKeys(final int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "k" + i)
                .toList();
    }

    /**
     * The one entry the filter wrote, read back out of its batched applyChanges call.
     *
     * <p>Writes are buffered and flushed as a single {@code applyChanges} at endProcessing rather
     * than a {@code create} per entry, so a test that wants the entry has to unwrap the batch.</p>
     */
    private TemporalEntry singleUpsertedEntry() {
        final ArgumentCaptor<ApplyChangesRequest> captor =
                ArgumentCaptor.forClass(ApplyChangesRequest.class);
        Mockito.verify(updatableTemporalStore, Mockito.times(1)).applyChanges(captor.capture());

        final List<ChangeOperation> ops = captor.getValue().getOperations();
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0).getType()).isEqualTo(ChangeOperation.Type.UPSERT);
        return ops.get(0).getEntry();
    }
}
