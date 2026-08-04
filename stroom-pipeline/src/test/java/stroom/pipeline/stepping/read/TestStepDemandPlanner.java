/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.stepping.read;

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.ReprocessPlanner.Decision;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.RecordRange;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.util.shared.ElementId;
import stroom.util.shared.OutputState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demand arithmetic {@code filteredWindowFor}'s sibling tests do not reach: which single record a step
 * is about ({@code demandedRecordFor} - the cross-stream guard, the filtered refusal, LAST's need for a
 * final extent, the cross-part neighbour walk) and how {@code onDemandRangeFor} widens it (REFRESH never;
 * navigation by the prefetch window, clamped to the feed's contiguous coverage). These are the off-by-one
 * shapes the integration walks exercise but cannot isolate.
 */
class TestStepDemandPlanner {

    private static final long META_ID = 77L;
    private static final String EDITED = "translationFilter";
    private static final String UPSTREAM = "combinedParser";
    private static final ElementFingerprints FINGERPRINTS = new ElementFingerprints(
            Map.of(EDITED, "own-e", UPSTREAM, "own-u"),
            Map.of(EDITED, "fp-e", UPSTREAM, "fp-u"));
    private static final Decision DECISION = Decision.reprocess(EDITED, UPSTREAM);

    private final StepDemandPlanner planner = new StepDemandPlanner(SteppingConfig::new);

    /** A store whose stream extent is {@code records} per part, with the upstream's output present for
     * exactly the given records of part 0 (sparse writes allowed - the feed a window is clamped against). */
    private StepDataStore storeWith(final Path dir, final int recordsPerPart, final int parts,
                                    final long... upstreamRecords) {
        final StepDataStore store = new StepDataStore(dir.resolve("s"), new SteppingConfig());
        for (int part = 0; part < parts; part++) {
            for (int r = 0; r < recordsPerPart; r++) {
                store.putRecord(new StepLocation(META_ID, part, r),
                        List.of(new StepDataStore.ElementRecord(new ElementId("marker"), "fp-m", data())), null);
            }
        }
        for (final long r : upstreamRecords) {
            store.putRecord(new StepLocation(META_ID, 0, r),
                    List.of(new StepDataStore.ElementRecord(new ElementId(UPSTREAM), "fp-u", data())),
                    null, null, StepDataStore.RecordOrder.ON_DEMAND);
        }
        return store;
    }

    private CapturedElementData data() {
        return new CapturedElementData(null, CapturedData.text("out"), false, false, true, null);
    }

    private PipelineStepRequest req(final StepType type, final StepLocation ref) {
        return PipelineStepRequest.builder().stepType(type).stepLocation(ref).build();
    }

    @Test
    void testARefreshIsNeverWidened(@TempDir final Path dir) {
        // REFRESH is the post-edit inner loop; widening it would multiply the cost of the one interaction
        // this design exists to keep fast, invisibly to every launch-counter test.
        final StepDataStore store = storeWith(dir, 100, 1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        final RecordRange range = planner.onDemandRangeFor(
                req(StepType.REFRESH, new StepLocation(META_ID, 0, 5)),
                null, DECISION, store, FINGERPRINTS, META_ID, true);
        assertThat(range).isEqualTo(new RecordRange(0, 5, 5));
    }

    @Test
    void testForwardIsWidenedByThePrefetchWindowOverAContiguousFeed(@TempDir final Path dir) {
        final StepDataStore store = storeWith(dir, 100, 1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        final RecordRange range = planner.onDemandRangeFor(
                req(StepType.FORWARD, new StepLocation(META_ID, 0, 2)),
                null, DECISION, store, FINGERPRINTS, META_ID, true);
        // Demanded record 3 at the near edge; default window 10 clamped to the feed's last record (9).
        assertThat(range).isEqualTo(new RecordRange(0, 3, 9));
    }

    @Test
    void testAForwardWindowStopsAtAHoleInTheFeed(@TempDir final Path dir) {
        // The replay reads each record's input from the feed and fails loudly on a gap, so a window must
        // never span one - and a hole's records are already materialised, exactly when prefetch has
        // nothing left to buy.
        final StepDataStore store = storeWith(dir, 100, 1, 0, 1, 2, 3, 4, 6, 7);
        final RecordRange range = planner.onDemandRangeFor(
                req(StepType.FORWARD, new StepLocation(META_ID, 0, 2)),
                null, DECISION, store, FINGERPRINTS, META_ID, true);
        assertThat(range).isEqualTo(new RecordRange(0, 3, 4));
    }

    @Test
    void testBackwardExtendsTheWindowDownward(@TempDir final Path dir) {
        // Replay is ascending, so for BACKWARD the demanded record commits last - the window extends below
        // it, not above.
        final StepDataStore store = storeWith(dir, 100, 1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        final RecordRange range = planner.onDemandRangeFor(
                req(StepType.BACKWARD, new StepLocation(META_ID, 0, 6)),
                null, DECISION, store, FINGERPRINTS, META_ID, true);
        assertThat(range.lastRecord()).as("the demanded record is the far edge").isEqualTo(5);
        assertThat(range.firstRecord()).as("widened downward over the feed").isEqualTo(0);
    }

    @Test
    void testARefreshNamingAnotherStreamDemandsNothingHere(@TempDir final Path dir) {
        final StepDataStore store = storeWith(dir, 10, 1);
        assertThat(planner.demandedRecordFor(
                req(StepType.REFRESH, new StepLocation(META_ID + 1, 0, 5)), META_ID, store, true))
                .as("a reference into another stream is not a demand on this one")
                .isNull();
    }

    @Test
    void testAFilteredNavigationCannotNameItsRecord(@TempDir final Path dir) {
        // "The next record" under a filter means "the next MATCH", unknowable without running records - the
        // windowed scan answers it instead.
        final StepDataStore store = storeWith(dir, 10, 1);
        final PipelineStepRequest filtered = PipelineStepRequest.builder()
                .stepType(StepType.FORWARD)
                .stepLocation(new StepLocation(META_ID, 0, 2))
                .stepFilterMap(Map.of(EDITED,
                        new SteppingFilterSettings(null, OutputState.NOT_EMPTY, List.of())))
                .build();
        assertThat(planner.demandedRecordFor(filtered, META_ID, store, true)).isNull();
    }

    @Test
    void testLastCanOnlyBeNamedOnceTheExtentIsFinal(@TempDir final Path dir) {
        // LAST asks where the stream ENDS; the last record captured so far is merely the last so far, and
        // answering from it would land mid-stream.
        final StepDataStore store = storeWith(dir, 10, 1);
        assertThat(planner.demandedRecordFor(req(StepType.LAST, null), META_ID, store, false)).isNull();
        assertThat(planner.demandedRecordFor(req(StepType.LAST, null), META_ID, store, true))
                .isEqualTo(new StepLocation(META_ID, 0, 9));
    }

    @Test
    void testForwardCrossesAPartBoundary(@TempDir final Path dir) {
        // Multi-part streams are invisible to the user; a FORWARD from a part's true end demands the first
        // record of the next part. The integration streams are single-part, so only a unit test reaches
        // this walk.
        final StepDataStore store = storeWith(dir, 5, 2);
        assertThat(planner.demandedRecordFor(
                req(StepType.FORWARD, new StepLocation(META_ID, 0, 4)), META_ID, store, true))
                .isEqualTo(new StepLocation(META_ID, 1, 0));
    }
}
