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

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStepDataStore {

    private static final long META_ID = 123L;
    private static final ElementId E1 = new ElementId("combinedParser");
    private static final ElementId E2 = new ElementId("translationFilter");
    private static final String FP_A = "aaaa";
    private static final String FP_B = "bbbb";
    private static final String FP_C = "cccc";
    private static final String FP_D = "dddd";

    private StepDataStore newStore(final Path tempDir, final SteppingConfig config) {
        return new StepDataStore(tempDir.resolve(Long.toString(META_ID)), config);
    }

    private SharedElementData data(final String input, final String output) {
        return new SharedElementData(input, output, null, false, false, output != null && !output.isBlank());
    }

    private StepLocation loc(final long part, final long record) {
        return new StepLocation(META_ID, part, record);
    }

    @Test
    void testRoundTripAndRandomAccess(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());

        store.putElementData(loc(0, 0), E1, FP_A, data("inA", "outA"));
        store.putElementData(loc(0, 1), E1, FP_A, data("inB", "outB"));
        store.putElementData(loc(0, 2), E1, FP_A, data("inC", "outC"));
        store.putElementData(loc(0, 3), E1, FP_A, data("inD", "outD"));

        assertThat(store.getRecordCount(0)).isEqualTo(4);
        assertThat(store.getPartCount()).isEqualTo(1);

        // Read out of order to prove random access.
        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).map(SharedElementData::getOutput).contains("outC");
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).map(SharedElementData::getInput).contains("inA");
        assertThat(store.getElementData(loc(0, 3), E1, FP_A)).map(SharedElementData::getOutput).contains("outD");

        final Optional<SharedElementData> rec1 = store.getElementData(loc(0, 1), E1, FP_A);
        assertThat(rec1).isPresent();
        assertThat(rec1.get().getInput()).isEqualTo("inB");
        assertThat(rec1.get().getOutput()).isEqualTo("outB");
        assertThat(rec1.get().isHasOutput()).isTrue();
    }

    @Test
    void testHasOutputAndNullsRoundTrip(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("in", null));
        store.putElementData(loc(0, 1), E1, FP_A, data(null, "  "));

        final SharedElementData r0 = store.getElementData(loc(0, 0), E1, FP_A).orElseThrow();
        assertThat(r0.getInput()).isEqualTo("in");
        assertThat(r0.getOutput()).isNull();
        assertThat(r0.isHasOutput()).isFalse();

        final SharedElementData r1 = store.getElementData(loc(0, 1), E1, FP_A).orElseThrow();
        assertThat(r1.getInput()).isNull();
        assertThat(r1.isHasOutput()).isFalse();
    }

    @Test
    void testMultipleElementsIndependent(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            store.putElementData(loc(0, r), E1, FP_A, data("e1in" + r, "e1out" + r));
            store.putElementData(loc(0, r), E2, FP_A, data("e2in" + r, "e2out" + r));
        }

        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).map(SharedElementData::getOutput).contains("e1out2");
        assertThat(store.getElementData(loc(0, 2), E2, FP_A)).map(SharedElementData::getInput).contains("e2in2");
        assertThat(store.getRecordCount(0)).isEqualTo(3);
    }

    @Test
    void testHasElementReuse(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("in", "out"));

        assertThat(store.hasElement(E1, FP_A)).isTrue();
        assertThat(store.hasElement(E1, FP_B)).isFalse();
        assertThat(store.hasElement(E2, FP_A)).isFalse();
    }

    @Test
    void testMissingReturnsEmpty(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("in", "out"));

        assertThat(store.getElementData(loc(0, 0), E2, FP_A)).isEmpty(); // unknown element
        assertThat(store.getElementData(loc(0, 0), E1, FP_B)).isEmpty(); // unknown fingerprint
        assertThat(store.getElementData(loc(0, 5), E1, FP_A)).isEmpty(); // record not yet written
    }

    @Test
    void testMultiPart(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        store.putElementData(loc(0, 1), E1, FP_A, data("b", "b"));
        store.putElementData(loc(1, 0), E1, FP_A, data("c", "c"));

        assertThat(store.getRecordCount(0)).isEqualTo(2);
        assertThat(store.getRecordCount(1)).isEqualTo(1);
        assertThat(store.getPartCount()).isEqualTo(2);
        assertThat(store.getElementData(loc(1, 0), E1, FP_A)).map(SharedElementData::getOutput).contains("c");
    }

    @Test
    void testOutOfOrderAppendThrows(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        assertThatThrownBy(() -> store.putElementData(loc(0, 2), E1, FP_A, data("c", "c")))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining("in order");
    }

    @Test
    void testMaxRecordSizeExceededThrows(@TempDir final Path tempDir) {
        final SteppingConfig tinyConfig = new SteppingConfig(
                null, null, null, 10L, null, null, null, null);
        final StepDataStore store = newStore(tempDir, tinyConfig);
        assertThatThrownBy(() -> store.putElementData(loc(0, 0), E1, FP_A,
                data("a-fairly-long-input-value", "and-output")))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void testMaxBytesPerStreamExceededThrows(@TempDir final Path tempDir) {
        // maxBytesPerStream = 150 bytes; records are ~100 bytes each so a couple trip the cap.
        final SteppingConfig config = new SteppingConfig(
                null, null, 150L, null, null, null, null, null);
        final StepDataStore store = newStore(tempDir, config);

        assertThatThrownBy(() -> {
            for (int r = 0; r < 1000; r++) {
                store.putElementData(loc(0, r), E1, FP_A, data("x".repeat(40), "y".repeat(40)));
            }
        })
                .isInstanceOf(StepDataStoreException.class)
                // Message unique to the stream byte cap (not the per-record size cap).
                .hasMessageContaining("narrow your selection")
                .hasMessageContaining("byte limit");
    }

    @Test
    void testMaxRecordsPerStreamExceededThrows(@TempDir final Path tempDir) {
        // maxRecordsPerStream = 3 (2nd ctor arg).
        final SteppingConfig config = new SteppingConfig(
                null, 3L, null, null, null, null, null, null);
        final StepDataStore store = newStore(tempDir, config);

        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        store.putElementData(loc(0, 1), E1, FP_A, data("b", "b"));
        store.putElementData(loc(0, 2), E1, FP_A, data("c", "c"));
        assertThatThrownBy(() -> store.putElementData(loc(0, 3), E1, FP_A, data("d", "d")))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining("record limit");
    }

    @Test
    void testNonContiguousWriteRejectedPriorIntact(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());

        // The first write establishes the base index; a subsequent non-contiguous (gap) write is rejected.
        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        assertThatThrownBy(() -> store.putElementData(loc(0, 2), E1, FP_A, data("c", "c")))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining("in order");

        // The already-written record is intact and the rejected one is absent.
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).map(SharedElementData::getOutput).contains("a");
        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).isEmpty();
    }

    @Test
    void testPutRecordWritesAllElementsAtomically(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            store.putRecord(loc(0, r), List.of(
                    new StepDataStore.ElementRecord(E1, FP_A, data("e1in" + r, "e1out" + r)),
                    new StepDataStore.ElementRecord(E2, FP_B, data("e2in" + r, "e2out" + r))));
        }
        assertThat(store.getRecordCount(0)).isEqualTo(3);
        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).map(SharedElementData::getOutput).contains("e1out2");
        assertThat(store.getElementData(loc(0, 2), E2, FP_B)).map(SharedElementData::getInput).contains("e2in2");
    }

    @Test
    void testPutRecordIsAllOrNothingOnCapFailure(@TempDir final Path tempDir) {
        // A record-size cap that the second element's IO exceeds; the whole record must be rejected so the
        // first element is NOT left committed (no torn record).
        final SteppingConfig config = new SteppingConfig(null, null, null, 60L, null, null, null, null);
        final StepDataStore store = newStore(tempDir, config);

        assertThatThrownBy(() -> store.putRecord(loc(0, 0), List.of(
                new StepDataStore.ElementRecord(E1, FP_A, data("ok", "ok")),
                new StepDataStore.ElementRecord(E2, FP_B, data("x".repeat(200), "y".repeat(200))))))
                .isInstanceOf(StepDataStoreException.class);

        assertThat(store.hasElement(E1, FP_A)).isFalse();
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).isEmpty();
        assertThat(store.getRecordCount(0)).isZero();
    }

    @Test
    void testNonZeroBaseRecordIndex(@TempDir final Path tempDir) {
        // Reader/text record detectors are 1-based; the store must preserve that indexing.
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 1), E1, FP_A, data("r1", "r1"));
        store.putElementData(loc(0, 2), E1, FP_A, data("r2", "r2"));
        store.putElementData(loc(0, 3), E1, FP_A, data("r3", "r3"));

        assertThat(store.getFirstRecordIndex(0)).isEqualTo(1);
        assertThat(store.getLastRecordIndex(0)).isEqualTo(3);
        assertThat(store.getRecordCount(0)).isEqualTo(3);
        assertThat(store.getElementData(loc(0, 1), E1, FP_A)).map(SharedElementData::getOutput).contains("r1");
        assertThat(store.getElementData(loc(0, 3), E1, FP_A)).map(SharedElementData::getOutput).contains("r3");
        // Indices outside the captured range read back empty.
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).isEmpty();
        assertThat(store.getElementData(loc(0, 4), E1, FP_A)).isEmpty();
    }

    @Test
    void testRetainLimitOfZeroStillKeepsCurrentWrite(@TempDir final Path tempDir) {
        final SteppingConfig config = new SteppingConfig().withMaxRetainedFingerprintsPerElement(0);
        final StepDataStore store = newStore(tempDir, config);

        store.putElementData(loc(0, 0), E1, FP_A, data("in", "out"));

        // A 0/negative retain limit must not delete the data just written.
        assertThat(store.hasElement(E1, FP_A)).isTrue();
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).map(SharedElementData::getOutput).contains("out");
    }

    @Test
    void testDottedElementIdIsContainedNotEscaped(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        final ElementId dotted = new ElementId("..");

        store.putElementData(loc(0, 0), dotted, FP_A, data("in", "out"));

        // Data round-trips and the dir name is the encoded form, not a literal ".." that would escape.
        assertThat(store.getElementData(loc(0, 0), dotted, FP_A)).map(SharedElementData::getInput).contains("in");
        assertThat(Files.exists(store.getStreamDir().resolve("0").resolve("%2E%2E").resolve(FP_A + ".dat")))
                .isTrue();
    }

    @Test
    void testByteBudgetReclaimedOnEviction(@TempDir final Path tempDir) {
        // maxBytesPerStream = 1000, retain only 1 fingerprint. Each new fingerprint evicts the previous,
        // so live bytes stay ~1 record even though far more than 1000 bytes are written in total. This
        // only holds if evicting a fingerprint reclaims its byte budget.
        final SteppingConfig config = new SteppingConfig(
                null, null, 1000L, null, null, 1, null, null);
        final StepDataStore store = newStore(tempDir, config);

        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                store.putElementData(loc(0, 0), E1, "fp" + i, data("x".repeat(40), "y".repeat(40)));
            }
        }).doesNotThrowAnyException();

        // Only the most recent fingerprint survives.
        assertThat(store.hasElement(E1, "fp99")).isTrue();
        assertThat(store.hasElement(E1, "fp98")).isFalse();
    }

    @Test
    void testEvictElement(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("in", "out"));
        final Path dataFile = store.getStreamDir()
                .resolve("0").resolve(E1.getId()).resolve(FP_A + ".dat");
        assertThat(Files.exists(dataFile)).isTrue();

        store.evictElement(E1, FP_A);

        assertThat(store.hasElement(E1, FP_A)).isFalse();
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).isEmpty();
        assertThat(Files.exists(dataFile)).isFalse();
    }

    @Test
    void testLruEvictionOfOldFingerprints(@TempDir final Path tempDir) {
        final SteppingConfig config = new SteppingConfig().withMaxRetainedFingerprintsPerElement(2);
        final StepDataStore store = newStore(tempDir, config);

        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        store.putElementData(loc(0, 0), E1, FP_B, data("b", "b"));
        // Adding a third fingerprint evicts the least-recently-used (FP_A).
        store.putElementData(loc(0, 0), E1, FP_C, data("c", "c"));

        assertThat(store.hasElement(E1, FP_A)).isFalse();
        assertThat(store.hasElement(E1, FP_B)).isTrue();
        assertThat(store.hasElement(E1, FP_C)).isTrue();

        // Touch FP_B (making FP_C the least-recently-used), then add FP_D -> FP_C evicted.
        assertThat(store.getElementData(loc(0, 0), E1, FP_B)).isPresent();
        store.putElementData(loc(0, 0), E1, FP_D, data("d", "d"));

        assertThat(store.hasElement(E1, FP_C)).isFalse();
        assertThat(store.hasElement(E1, FP_B)).isTrue();
        assertThat(store.hasElement(E1, FP_D)).isTrue();
    }

    @Test
    void testDeleteAll(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("in", "out"));
        final Path streamDir = store.getStreamDir();
        assertThat(Files.exists(streamDir)).isTrue();

        store.deleteAll();

        assertThat(Files.exists(streamDir)).isFalse();
        assertThatThrownBy(() -> store.getElementData(loc(0, 0), E1, FP_A))
                .isInstanceOf(StepDataStoreException.class);
    }
}
