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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.read.StoreStepResolver.CapturedRange;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Coverage} - the one question the read side asks of a producer: which records are held, and is the
 * extent final. The dangerous direction is the permissive one, in either half: {@code holds} answering true
 * for a record not materialised serves a blank pane, and {@code isExtentFinal} answering true early turns
 * "not captured yet" into "does not exist" and steps over records into the next stream. Both directions are
 * pinned here for each of the three providers.
 */
class TestCoverage {

    private static final long META_ID = 9L;
    private static final ElementId E1 = new ElementId("e1");
    private static final String FP = "fpA";

    private StepDataStore store(final Path dir) {
        return new StepDataStore(dir.resolve(Long.toString(META_ID)), new SteppingConfig());
    }

    private CapturedElementData data(final String value) {
        return new CapturedElementData(
                CapturedData.text(value), CapturedData.text(value), false, false, true, null);
    }

    private void put(final StepDataStore store, final long record, final StepDataStore.RecordOrder order) {
        store.putRecord(new StepLocation(META_ID, 0, record),
                List.of(new StepDataStore.ElementRecord(E1, FP, data("out" + record))),
                null, Map.of(), null, order);
    }

    // --- element coverage -----------------------------------------------------------------------

    @Test
    void testElementCoverageCoversUpToWhatIsHeld(@TempDir final Path dir) {
        final StepDataStore store = store(dir);
        for (long r = 0; r < 5; r++) {
            put(store, r, StepDataStore.RecordOrder.SEQUENTIAL);
        }
        final Coverage coverage = store.elementCoverage(E1, FP, () -> false);

        assertThat(coverage.first(0)).isEqualTo(0);
        assertThat(coverage.last(0)).isEqualTo(4);
        assertThat(coverage.holds(0, 0)).isTrue();
        assertThat(coverage.holds(0, 4)).isTrue();
    }

    @Test
    void testElementCoverageDoesNotCoverWhatWasAskedBeyondIt(@TempDir final Path dir) {
        // The other direction - the one that matters. Asked past the frontier, the answer must be no.
        final StepDataStore store = store(dir);
        for (long r = 0; r < 5; r++) {
            put(store, r, StepDataStore.RecordOrder.SEQUENTIAL);
        }
        final Coverage coverage = store.elementCoverage(E1, FP, () -> false);

        assertThat(coverage.holds(0, 5)).as("one past the frontier").isFalse();
        assertThat(coverage.holds(0, 100)).as("far past it").isFalse();
        assertThat(coverage.holds(1, 0)).as("a part with nothing holds nothing").isFalse();
        assertThat(coverage.first(1)).isEqualTo(Coverage.NONE);
        assertThat(coverage.last(1)).isEqualTo(Coverage.NONE);
    }

    @Test
    void testASparseElementReportsItsHolesHonestly(@TempDir final Path dir) {
        // The whole reason holds() exists apart from the bounds: an on-demand element has gaps inside its
        // own span, and spanning over them is the blank-pane bug.
        final StepDataStore store = store(dir);
        put(store, 2, StepDataStore.RecordOrder.ON_DEMAND);
        put(store, 7, StepDataStore.RecordOrder.ON_DEMAND);
        final Coverage coverage = store.elementCoverage(E1, FP, () -> false);

        assertThat(coverage.first(0)).isEqualTo(2);
        assertThat(coverage.last(0)).isEqualTo(7);
        assertThat(coverage.holds(0, 2)).isTrue();
        assertThat(coverage.holds(0, 7)).isTrue();
        assertThat(coverage.holds(0, 4)).as("inside the span, but a hole").isFalse();
    }

    @Test
    void testCoverageIsLiveNotASnapshot(@TempDir final Path dir) {
        // Handed out once, queried while the producer runs. A snapshot would hide records that have arrived.
        final StepDataStore store = store(dir);
        final Coverage coverage = store.elementCoverage(E1, FP, () -> false);
        assertThat(coverage.holds(0, 0)).isFalse();

        put(store, 0, StepDataStore.RecordOrder.SEQUENTIAL);

        assertThat(coverage.holds(0, 0)).as("the record that just landed is visible").isTrue();
        assertThat(coverage.last(0)).isEqualTo(0);
    }

    @Test
    void testAnUnknownFingerprintCoversNothing(@TempDir final Path dir) {
        final StepDataStore store = store(dir);
        put(store, 0, StepDataStore.RecordOrder.SEQUENTIAL);
        final Coverage coverage = store.elementCoverage(E1, "other-fp", () -> false);

        assertThat(coverage.holds(0, 0)).isFalse();
        assertThat(coverage.first(0)).isEqualTo(Coverage.NONE);
    }

    // --- store-wide record coverage -------------------------------------------------------------

    @Test
    void testRecordCoverageFollowsTheStateFileIncludingHoles(@TempDir final Path dir) {
        // The state file is un-fingerprinted and every committed record occupies a segment, so it is the
        // honest answer to "which records exist at all" - including the holes on-demand writes punch.
        final StepDataStore store = store(dir);
        put(store, 0, StepDataStore.RecordOrder.SEQUENTIAL);
        put(store, 1, StepDataStore.RecordOrder.SEQUENTIAL);
        put(store, 50, StepDataStore.RecordOrder.ON_DEMAND);
        final Coverage coverage = store.recordCoverage(() -> false);

        assertThat(coverage.first(0)).isEqualTo(0);
        assertThat(coverage.last(0)).isEqualTo(50);
        assertThat(coverage.holds(0, 1)).isTrue();
        assertThat(coverage.holds(0, 50)).isTrue();
        assertThat(coverage.holds(0, 25)).as("between the sweep's frontier and the replayed record")
                .isFalse();
    }

    @Test
    void testPartsEnumerateOnlyWhatIsHeld(@TempDir final Path dir) {
        // Element coverage lists the parts THIS element has records in - not the stream's parts. An element
        // reprocessed for one part must not claim the others.
        final StepDataStore store = store(dir);
        put(store, 0, StepDataStore.RecordOrder.SEQUENTIAL);
        store.putRecord(new StepLocation(META_ID, 2, 0),
                List.of(new StepDataStore.ElementRecord(new ElementId("other"), FP, data("x"))),
                null, Map.of(), null, StepDataStore.RecordOrder.SEQUENTIAL);

        assertThat(store.elementCoverage(E1, FP, () -> false).parts())
                .as("e1 only has part 0; part 2 belongs to another element").containsExactly(0L);
        assertThat(store.recordCoverage(() -> false).parts())
                .as("the stream has both").containsExactly(0L, 2L);
    }

    // --- extent finality ------------------------------------------------------------------------

    @Test
    void testExtentFinalityIsTheSuppliersAnswerAskedEveryTime(@TempDir final Path dir) {
        // Producer knowledge, read through live - a coverage created while the producer ran must say final
        // once it finishes.
        final boolean[] finished = {false};
        final Coverage coverage = store(dir).elementCoverage(E1, FP, () -> finished[0]);

        assertThat(coverage.isExtentFinal()).isFalse();
        finished[0] = true;
        assertThat(coverage.isExtentFinal()).as("finality follows the producer, not creation time").isTrue();
    }

    // --- the sweep's watermark coverage ---------------------------------------------------------

    @Test
    void testSweepCoverageTracksWhatWasSignalledNotWhatIsStored(@TempDir final Path dir) {
        // A reprocess writes into a store whose reused upstream spans the whole stream; the sweep's own
        // coverage must reach only as far as the producer has SIGNALLED, or a step lands on a record whose
        // re-produced element is not there yet - the readiness bug this distinction fixed.
        final StepDataStore store = store(dir);
        for (long r = 0; r < 10; r++) {
            put(store, r, StepDataStore.RecordOrder.SEQUENTIAL);
        }
        final StreamSweep sweep = new StreamSweep(META_ID, store);
        sweep.recordCaptured(new StepLocation(META_ID, 0, 0));
        sweep.recordCaptured(new StepLocation(META_ID, 0, 1));
        final Coverage coverage = sweep.coverage();

        assertThat(store.getLastRecordIndex(0)).as("the store holds far more").isEqualTo(9);
        assertThat(coverage.last(0)).as("but coverage is what was signalled").isEqualTo(1);
        assertThat(coverage.holds(0, 1)).isTrue();
        assertThat(coverage.holds(0, 2)).isFalse();
        assertThat(coverage.isExtentFinal()).isFalse();

        sweep.markFullyCaptured();
        assertThat(coverage.isExtentFinal()).isTrue();
    }

    @Test
    void testAnErroredSweepsExtentIsFinal(@TempDir final Path dir) {
        // An errored capture adds no more records either; a non-final answer would leave a waiter waiting
        // for records that will never come (§5 - everything must signal).
        final StreamSweep sweep = new StreamSweep(META_ID, store(dir));
        sweep.recordCaptured(new StepLocation(META_ID, 0, 0));
        sweep.markError(new RuntimeException("boom"));

        assertThat(sweep.coverage().isExtentFinal()).isTrue();
    }

    // --- the CapturedRange view -----------------------------------------------------------------

    @Test
    void testTheRangeViewIsTheCoverageAnswerForAnswer(@TempDir final Path dir) {
        // CapturedRange is coverage's navigation view, not a reinterpretation: same bounds, and contains()
        // is holds() - so a sparse coverage's holes survive the translation.
        final StepDataStore store = store(dir);
        put(store, 2, StepDataStore.RecordOrder.ON_DEMAND);
        put(store, 7, StepDataStore.RecordOrder.ON_DEMAND);
        final Coverage coverage = store.elementCoverage(E1, FP, () -> false);
        final CapturedRange view = CapturedRange.of(coverage);

        assertThat(view.first(0)).isEqualTo(coverage.first(0));
        assertThat(view.last(0)).isEqualTo(coverage.last(0));
        assertThat(view.holds(0, 2)).isTrue();
        assertThat(view.holds(0, 4)).as("the hole survives the view - no bounds-based default").isFalse();
        assertThat(view.holds(0, 7)).isTrue();
    }
}
