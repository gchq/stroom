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

import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    private CapturedElementData data(final String input, final String output) {
        return new CapturedElementData(CapturedData.text(input), CapturedData.text(output),
                false, false, output != null && !output.isBlank(), null);
    }

    private StepLocation loc(final long part, final long record) {
        return new StepLocation(META_ID, part, record);
    }

    private SourceLocation sourceLocation(final long record, final int lineFrom) {
        return SourceLocation.builder(META_ID)
                .withPartIndex(0L)
                .withRecordIndex(record)
                .withHighlight(new TextRange(new DefaultLocation(lineFrom, 1), new DefaultLocation(lineFrom, 40)))
                .build();
    }

    private StepDataStore.ElementRecord rec(final ElementId elementId, final String fingerprint, final String out) {
        return new StepDataStore.ElementRecord(elementId, fingerprint, data("in", out));
    }

    @Test
    void testSourceLocationSnapshotRoundTripAndRandomAccess(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "out" + r)), sourceLocation(r, 10 + r));
        }

        // Random access: each record's snapshot round-trips with its own highlight.
        assertThat(store.getSourceLocation(loc(0, 2)).orElseThrow()
                .getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(12);
        assertThat(store.getSourceLocation(loc(0, 0)).orElseThrow()
                .getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(10);
        // A record not yet written has no snapshot.
        assertThat(store.getSourceLocation(loc(0, 5))).isEmpty();
    }

    @Test
    void testNullSourceLocationSnapshotReadsBackEmptyButRecordExists(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putRecord(loc(0, 0), List.of(rec(E1, FP_A, "out")), null);

        // The element IO is present, but the record carried no source-location snapshot.
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).isPresent();
        assertThat(store.getSourceLocation(loc(0, 0))).isEmpty();
    }

    @Test
    void testSourceLocationSnapshotSkippedAndPreservedOnResweep(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 2; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "out" + r)), sourceLocation(r, 10 + r));
        }

        // Re-sweep after a downstream edit: E1 is unchanged (FP_A already present -> skipped) and a
        // downstream element gets a new fingerprint. The unfingerprinted source-location snapshot for each
        // record is already present, so it must be skipped (not trip the in-order check) and NOT overwritten.
        assertThatCode(() -> {
            for (int r = 0; r < 2; r++) {
                store.putRecord(loc(0, r),
                        List.of(rec(E1, FP_A, "out" + r), rec(E2, FP_B, "e2out" + r)),
                        sourceLocation(r, 999)); // a different location; must be ignored (already present)
            }
        }).doesNotThrowAnyException();

        // The original snapshot survives the re-sweep untouched.
        assertThat(store.getSourceLocation(loc(0, 1)).orElseThrow()
                .getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(11);
        // The re-swept downstream element is readable.
        assertThat(store.getElementData(loc(0, 1), E2, FP_B)).map(CapturedElementData::outputText)
                .contains("e2out1");
    }

    @Test
    void testSourceLocationSnapshotDeletedWithStore(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putRecord(loc(0, 0), List.of(rec(E1, FP_A, "out")), sourceLocation(0, 10));
        assertThat(store.getSourceLocation(loc(0, 0))).isPresent();

        store.deleteAll();

        assertThatThrownBy(() -> store.getSourceLocation(loc(0, 0)))
                .isInstanceOf(StepDataStoreException.class);
    }

    @Test
    void testCompletenessIsNotTheSameAsPresence(@TempDir final Path tempDir) {
        // The distinction the reuse plan turns on. A sweep either captured a stream or it did not, so
        // presence used to be a fair proxy for reusable; once records are materialised one at a time an
        // element is present long before it is reusable, and treating it as reusable makes the planner
        // decide there is nothing left to reprocess.
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 4; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "swept" + r)));
        }
        // E2 has one record materialised out of the four the stream has.
        store.putRecord(loc(0, 2), List.of(rec(E2, FP_B, "materialised")), null, null,
                StepDataStore.RecordOrder.ON_DEMAND);

        assertThat(store.hasElement(E2, FP_B)).as("something is stored for it").isTrue();
        assertThat(store.hasCompleteElement(E2, FP_B)).as("but it is not reusable").isFalse();
        assertThat(store.hasCompleteElement(E1, FP_A)).as("the swept element is").isTrue();
        assertThat(store.hasCompleteElement(E1, FP_C)).as("an absent fingerprint is not").isFalse();
    }

    @Test
    void testContiguousMaterialisationIsStillIncomplete(@TempDir final Path tempDir) {
        // The trap: records 0 and 1 materialised are contiguous, so the element's own span matches its own
        // count. Completeness has to be measured against the STREAM's range, not the element's.
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 5; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "swept" + r)));
        }
        store.putRecord(loc(0, 0), List.of(rec(E2, FP_B, "m0")), null, null,
                StepDataStore.RecordOrder.ON_DEMAND);
        store.putRecord(loc(0, 1), List.of(rec(E2, FP_B, "m1")), null, null,
                StepDataStore.RecordOrder.ON_DEMAND);

        assertThat(store.hasCompleteElement(E2, FP_B))
                .as("two contiguous records out of five is not the whole stream").isFalse();
    }

    @Test
    void testAnElementMissingFromAPartIsIncomplete(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putRecord(loc(0, 0), List.of(rec(E1, FP_A, "p0"), rec(E2, FP_B, "p0")));
        store.putRecord(loc(1, 0), List.of(rec(E1, FP_A, "p1")));

        assertThat(store.hasCompleteElement(E1, FP_A)).as("present in both parts").isTrue();
        assertThat(store.hasCompleteElement(E2, FP_B)).as("absent from the second part").isFalse();
    }

    @Test
    void testAnEmptyStoreHasNoCompleteElements(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        assertThat(store.hasCompleteElement(E1, FP_A)).isFalse();
    }

    @Test
    void testOnDemandWritesNeedNotBeInOrder(@TempDir final Path tempDir) {
        // Materialising the record the user is looking at means writing one record, deep into the stream,
        // into an otherwise empty file - and later another, with nothing in between.
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putRecord(loc(0, 5_000), List.of(rec(E1, FP_A, "deep")), null, null,
                StepDataStore.RecordOrder.ON_DEMAND);
        store.putRecord(loc(0, 12), List.of(rec(E1, FP_A, "shallow")), null, null,
                StepDataStore.RecordOrder.ON_DEMAND);

        assertThat(store.getElementData(loc(0, 5_000), E1, FP_A)).map(CapturedElementData::outputText)
                .contains("deep");
        assertThat(store.getElementData(loc(0, 12), E1, FP_A)).map(CapturedElementData::outputText)
                .contains("shallow");
        // A record between them was never materialised, and must not read back as anything.
        assertThat(store.getElementData(loc(0, 100), E1, FP_A)).isEmpty();
    }


    @Test
    void testAnOnDemandWriteCanFollowASweep(@TempDir final Path tempDir) {
        // The real shape: a sweep captured the stream under one fingerprint, then an edit materialises a
        // single record under a NEW fingerprint. The new file starts at that record, not at 0.
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 5; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "swept" + r)));
        }

        store.putRecord(loc(0, 3), List.of(rec(E1, FP_B, "edited3")), null, null,
                StepDataStore.RecordOrder.ON_DEMAND);

        assertThat(store.getElementData(loc(0, 3), E1, FP_B)).map(CapturedElementData::outputText)
                .contains("edited3");
        assertThat(store.getElementData(loc(0, 3), E1, FP_A)).map(CapturedElementData::outputText)
                .as("the swept version is untouched").contains("swept3");
        assertThat(store.getElementData(loc(0, 4), E1, FP_B))
                .as("only the visited record was materialised").isEmpty();
    }

    @Test
    void testScopeMapRoundTripAndRandomAccess(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "out" + r)), sourceLocation(r, 10 + r),
                    Map.of("user", "user" + r));
        }

        assertThat(store.getScopeMap(loc(0, 2))).containsExactlyEntriesOf(Map.of("user", "user2"));
        assertThat(store.getScopeMap(loc(0, 0))).containsExactlyEntriesOf(Map.of("user", "user0"));
        // Both halves of the snapshot share a segment, so neither may disturb the other.
        assertThat(store.getSourceLocation(loc(0, 2)).orElseThrow()
                .getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(12);
        // A record that was never written has no scope map rather than a null one.
        assertThat(store.getScopeMap(loc(0, 5))).isEmpty();
    }

    @Test
    void testAbsentScopeMapReadsBackEmpty(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putRecord(loc(0, 0), List.of(rec(E1, FP_A, "out")), sourceLocation(0, 10));

        assertThat(store.getScopeMap(loc(0, 0))).isEmpty();
        assertThat(store.getSourceLocation(loc(0, 0))).isPresent();
    }

    @Test
    void testScopeMapPreservedOnResweep(@TempDir final Path tempDir) {
        // The snapshot a reprocess needs is the one the FULL sweep took, because it holds the puts made by
        // the elements the reprocess is not re-running. A re-sweep after an edit must therefore leave it
        // alone, exactly as it leaves the unchanged element files alone.
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            store.putRecord(loc(0, r), List.of(rec(E1, FP_A, "out" + r)), sourceLocation(r, 10),
                    Map.of("upstream", "swept" + r));
        }

        for (int r = 0; r < 3; r++) {
            store.putRecord(loc(0, r),
                    List.of(rec(E1, FP_A, "out" + r), rec(E2, FP_B, "edited" + r)),
                    sourceLocation(r, 999),
                    Map.of("upstream", "overwritten"));
        }

        assertThat(store.getScopeMap(loc(0, 1))).containsExactlyEntriesOf(Map.of("upstream", "swept1"));
        assertThat(store.getElementData(loc(0, 1), E2, FP_B)).map(CapturedElementData::outputText)
                .contains("edited1");
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
        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).map(CapturedElementData::outputText).contains("outC");
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).map(CapturedElementData::inputText).contains("inA");
        assertThat(store.getElementData(loc(0, 3), E1, FP_A)).map(CapturedElementData::outputText).contains("outD");

        final Optional<CapturedElementData> rec1 = store.getElementData(loc(0, 1), E1, FP_A);
        assertThat(rec1).isPresent();
        assertThat(rec1.get().inputText()).isEqualTo("inB");
        assertThat(rec1.get().outputText()).isEqualTo("outB");
        assertThat(rec1.get().hasOutput()).isTrue();
    }

    @Test
    void testHasOutputAndNullsRoundTrip(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        store.putElementData(loc(0, 0), E1, FP_A, data("in", null));
        store.putElementData(loc(0, 1), E1, FP_A, data(null, "  "));

        final CapturedElementData r0 = store.getElementData(loc(0, 0), E1, FP_A).orElseThrow();
        assertThat(r0.inputText()).isEqualTo("in");
        assertThat(r0.outputText()).isNull();
        assertThat(r0.hasOutput()).isFalse();

        final CapturedElementData r1 = store.getElementData(loc(0, 1), E1, FP_A).orElseThrow();
        assertThat(r1.inputText()).isNull();
        assertThat(r1.hasOutput()).isFalse();
    }

    @Test
    void testMultipleElementsIndependent(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            store.putElementData(loc(0, r), E1, FP_A, data("e1in" + r, "e1out" + r));
            store.putElementData(loc(0, r), E2, FP_A, data("e2in" + r, "e2out" + r));
        }

        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).map(CapturedElementData::outputText).contains("e1out2");
        assertThat(store.getElementData(loc(0, 2), E2, FP_A)).map(CapturedElementData::inputText).contains("e2in2");
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
        assertThat(store.getElementData(loc(1, 0), E1, FP_A)).map(CapturedElementData::outputText).contains("c");
    }


    @Test
    void testMaxRecordSizeExceededThrows(@TempDir final Path tempDir) {
        final SteppingConfig tinyConfig = new SteppingConfig(
                null, null, null, 10L, null, null, null, null, null, null, null, null);
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
                null, null, 150L, null, null, null, null, null, null, null, null, null);
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
                null, 3L, null, null, null, null, null, null, null, null, null, null);
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
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).map(CapturedElementData::outputText).contains("a");
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
        assertThat(store.getElementData(loc(0, 2), E1, FP_A)).map(CapturedElementData::outputText).contains("e1out2");
        assertThat(store.getElementData(loc(0, 2), E2, FP_B)).map(CapturedElementData::inputText).contains("e2in2");
    }

    @Test
    void testPutRecordIsAllOrNothingOnCapFailure(@TempDir final Path tempDir) {
        // A record-size cap that the second element's IO exceeds; the whole record must be rejected so the
        // first element is NOT left committed (no torn record).
        final SteppingConfig config = new SteppingConfig(
                null, null, null, 60L, null, null, null, null, null, null, null, null);
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
        assertThat(store.getElementData(loc(0, 1), E1, FP_A)).map(CapturedElementData::outputText).contains("r1");
        assertThat(store.getElementData(loc(0, 3), E1, FP_A)).map(CapturedElementData::outputText).contains("r3");
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
        assertThat(store.getElementData(loc(0, 0), E1, FP_A)).map(CapturedElementData::outputText).contains("out");
    }

    @Test
    void testDottedElementIdIsContainedNotEscaped(@TempDir final Path tempDir) {
        final StepDataStore store = newStore(tempDir, new SteppingConfig());
        final ElementId dotted = new ElementId("..");

        store.putElementData(loc(0, 0), dotted, FP_A, data("in", "out"));

        // Data round-trips and the dir name is the encoded form, not a literal ".." that would escape.
        assertThat(store.getElementData(loc(0, 0), dotted, FP_A)).map(CapturedElementData::inputText).contains("in");
        assertThat(Files.exists(store.getStreamDir().resolve("0").resolve("%2E%2E").resolve(FP_A + ".dat")))
                .isTrue();
    }

    @Test
    void testByteBudgetReclaimedOnEviction(@TempDir final Path tempDir) {
        // maxBytesPerStream = 1000, retain only 1 fingerprint. Each new fingerprint evicts the previous,
        // so live bytes stay ~1 record even though far more than 1000 bytes are written in total. This
        // only holds if evicting a fingerprint reclaims its byte budget.
        final SteppingConfig config = new SteppingConfig(
                null, null, 1000L, null, null, 1, null, null, null, null, null, null);
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
    void testPinnedFingerprintIsNotEvicted(@TempDir final Path tempDir) {
        // Retain 1 version per element: without a pin, every new fingerprint evicts the previous one - that
        // is exactly what testLruEvictionOfOldFingerprints and the negative control below assert. Here FP_A
        // is in use (a producer writing it, or a read serving from it), so it must survive versions that
        // would otherwise retire it.
        final SteppingConfig config = new SteppingConfig().withMaxRetainedFingerprintsPerElement(1);
        final StepDataStore store = newStore(tempDir, config);

        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        try (final StorePin pin = store.pin(Map.of(E1.getId(), FP_A))) {
            store.putElementData(loc(0, 0), E1, FP_B, data("b", "b"));
            store.putElementData(loc(0, 0), E1, FP_C, data("c", "c"));

            // The pinned version survives, even though the retention limit is 1.
            assertThat(store.hasElement(E1, FP_A)).isTrue();
            // FP_B was not pinned, so it was evicted as usual - the limit still bites where it can.
            assertThat(store.hasElement(E1, FP_B)).isFalse();
            assertThat(store.hasElement(E1, FP_C)).isTrue();
            assertThat(store.getElementData(loc(0, 0), E1, FP_A))
                    .map(CapturedElementData::outputText).contains("a");
            // A read refreshes recency but never deletes: under the old read-evicts semantics the read of
            // FP_A just above would have retired FP_C (limit 1, FP_A pinned) - so this assert is the
            // negative control for "eviction is enforced only on writes".
            assertThat(store.hasElement(E1, FP_C))
                    .as("a read never evicts a sibling version")
                    .isTrue();
        }

        // Released: FP_A is ordinary history again and the next write retires it.
        store.putElementData(loc(0, 0), E1, FP_D, data("d", "d"));
        assertThat(store.hasElement(E1, FP_A)).isFalse();
        assertThat(store.hasElement(E1, FP_D)).isTrue();
    }

    @Test
    void testUnpinnedFingerprintStillEvicts(@TempDir final Path tempDir) {
        // NEGATIVE CONTROL for the test above: same shape, but the pin is on a DIFFERENT element's version.
        // If pinning were doing nothing (or pinning everything), one of these two assertions would fail.
        final SteppingConfig config = new SteppingConfig().withMaxRetainedFingerprintsPerElement(1);
        final StepDataStore store = newStore(tempDir, config);

        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        store.putElementData(loc(0, 0), E2, FP_A, data("a", "a"));
        try (final StorePin pin = store.pin(Map.of(E2.getId(), FP_A))) {
            store.putElementData(loc(0, 0), E1, FP_B, data("b", "b"));
            store.putElementData(loc(0, 0), E2, FP_B, data("b", "b"));

            assertThat(store.hasElement(E1, FP_A)).isFalse();
            assertThat(store.hasElement(E2, FP_A)).isTrue();
        }
    }

    @Test
    void testOverlappingPinsAreIndependent(@TempDir final Path tempDir) {
        // Two things using the same version at once - a capture writing it and a step reading it, say. The
        // first to finish must not release the other's claim.
        final SteppingConfig config = new SteppingConfig().withMaxRetainedFingerprintsPerElement(1);
        final StepDataStore store = newStore(tempDir, config);

        store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
        final StorePin first = store.pin(Map.of(E1.getId(), FP_A));
        final StorePin second = store.pin(Map.of(E1.getId(), FP_A));

        // Closing twice must not decrement twice either, or it would free the other holder's claim.
        first.close();
        first.close();
        store.putElementData(loc(0, 0), E1, FP_B, data("b", "b"));
        assertThat(store.hasElement(E1, FP_A)).isTrue();

        second.close();
        store.putElementData(loc(0, 0), E1, FP_C, data("c", "c"));
        assertThat(store.hasElement(E1, FP_A)).isFalse();
    }

    @Test
    void testAPinTakenBeforeTheDataIsWrittenStillProtectsIt(@TempDir final Path tempDir) {
        // A producer pins what it is ABOUT to write - the version does not exist in the store yet. The claim
        // has to apply to the file when it appears, not just to one already there.
        final SteppingConfig config = new SteppingConfig().withMaxRetainedFingerprintsPerElement(1);
        final StepDataStore store = newStore(tempDir, config);

        try (final StorePin pin = store.pin(Map.of(E1.getId(), FP_A))) {
            store.putElementData(loc(0, 0), E1, FP_A, data("a", "a"));
            store.putElementData(loc(0, 0), E1, FP_B, data("b", "b"));

            assertThat(store.hasElement(E1, FP_A)).isTrue();
        }
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
