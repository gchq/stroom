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
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A sweep and a single-record replay writing to the same store.
 * <p>
 * They do meet. A session's store is shared by every sweep of a stream, the per-part state file carries no
 * fingerprint at all, and an element file is shared by a sweep and a replay whenever both run under the same
 * fingerprint. Editing an XSLT mid-sweep <em>abandons</em> the sweep rather than racing it, but abandonment is
 * a cooperative flag - the sweep runs on until its next record boundary - so both writers are briefly live at
 * once. {@link StepDataStore} is a monitor, so that overlap cannot tear a write.
 * <p>
 * What it could do, until this was fixed, is reject one. The in-order check assumed a file's records form a
 * contiguous run, which held while a sweep was the only writer and stopped holding the moment records could be
 * materialised on demand: {@code nextRecordIndex()} is {@code base + count}, which is meaningless once there
 * are holes. A sweep resuming at record 10 after a replay had written record 50 was told "expected index 11
 * but got 10" and failed. Neither case here needs any concurrency to reproduce - the overlap window only makes
 * them more likely.
 * <p>
 * The check is kept for files a sweep alone has written, because it is what catches a producer that has lost
 * its place - the bug where a replayed record was captured as record 0.
 */
class TestSweepAndReplaySharingAStore {

    private static final long META_ID = 1L;
    private static final ElementId E1 = new ElementId("e1");

    private StepDataStore store(final Path dir, final String name) {
        return new StepDataStore(dir.resolve(name), new SteppingConfig());
    }

    private CapturedElementData data(final String value) {
        return new CapturedElementData(
                CapturedData.text(value), CapturedData.text(value), false, false, true, null);
    }

    private StepDataStore.ElementRecord rec(final String fingerprint, final String value) {
        return new StepDataStore.ElementRecord(E1, fingerprint, data(value));
    }

    private void put(final StepDataStore store,
                     final long recordIndex,
                     final String fingerprint,
                     final StepDataStore.RecordOrder order) {
        store.putRecord(new StepLocation(META_ID, 0, recordIndex),
                List.of(rec(fingerprint, "out" + recordIndex)), null, Map.of(), null, order);
    }

    @Test
    void testASweepResumesAfterAReplayPunchedAHoleInTheSharedStateFile(@TempDir final Path dir) {
        // The state file is un-fingerprinted, so a sweep and a replay of the *edited* pipeline both write it.
        final StepDataStore store = store(dir, "state");
        for (int r = 0; r < 10; r++) {
            put(store, r, "fpA", StepDataStore.RecordOrder.SEQUENTIAL);
        }
        put(store, 50, "fpB", StepDataStore.RecordOrder.ON_DEMAND);

        assertThatCode(() -> put(store, 10, "fpA", StepDataStore.RecordOrder.SEQUENTIAL))
                .as("the sweep's next record is still its next record, hole at 50 notwithstanding")
                .doesNotThrowAnyException();
    }

    @Test
    void testASweepCapturesAnElementAReplayHasAlreadyTouched(@TempDir final Path dir) {
        // Same fingerprint, so the same element file: a replay materialises one mid-stream record, and a
        // later step needs the whole stream, so a sweep starts from the beginning of it.
        final StepDataStore store = store(dir, "element");
        put(store, 50, "fpA", StepDataStore.RecordOrder.ON_DEMAND);

        assertThatCode(() -> put(store, 0, "fpA", StepDataStore.RecordOrder.SEQUENTIAL))
                .as("a sweep may start from record 0 even though record 50 is already there")
                .doesNotThrowAnyException();
    }

    @Test
    void testTheSweepAndTheReplayBothRemainReadable(@TempDir final Path dir) {
        // Not just "does not throw": both writers' data has to survive, since the whole point of sharing the
        // store is that the elements an edit did not touch are reused rather than recaptured.
        final StepDataStore store = store(dir, "both");
        put(store, 0, "fpA", StepDataStore.RecordOrder.SEQUENTIAL);
        put(store, 50, "fpB", StepDataStore.RecordOrder.ON_DEMAND);
        put(store, 1, "fpA", StepDataStore.RecordOrder.SEQUENTIAL);

        assertThat(store, 0, "fpA");
        assertThat(store, 1, "fpA");
        assertThat(store, 50, "fpB");
    }

    private void assertThat(final StepDataStore store, final long recordIndex, final String fingerprint) {
        org.assertj.core.api.Assertions
                .assertThat(store.getElementData(new StepLocation(META_ID, 0, recordIndex), E1, fingerprint))
                .as("record " + recordIndex + " under " + fingerprint + " is readable")
                .isPresent();
    }

    @Test
    void testAPureSweepStillHasToAppendInOrder(@TempDir final Path dir) {
        // The negative control for the fix. Relaxing the check for files a replay has touched must not relax
        // it everywhere - on a file only a sweep has written, a record arriving out of order still means the
        // producer has lost its place, which is exactly how the replayed-record-numbered-0 bug was caught.
        final StepDataStore store = store(dir, "pure");
        put(store, 0, "fpA", StepDataStore.RecordOrder.SEQUENTIAL);
        put(store, 1, "fpA", StepDataStore.RecordOrder.SEQUENTIAL);

        assertThatThrownBy(() -> put(store, 7, "fpA", StepDataStore.RecordOrder.SEQUENTIAL))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining("expected index 2 but got 7");
    }
}
