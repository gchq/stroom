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

import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.StoreStepResolver.ResolvedStep;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.OutputState;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoreStepResolver {

    private static final long META = 1L;
    private static final String E1 = "e1";
    private static final String E2 = "e2";

    private final StoreStepResolver resolver = new StoreStepResolver();
    // Active fingerprints: e1 -> fp1, e2 -> fp2. Own map is unused by the resolver.
    private final ElementFingerprints fingerprints = new ElementFingerprints(
            Map.of(E1, "o1", E2, "o2"),
            Map.of(E1, "fp1", E2, "fp2"));

    private CapturedElementData ed(final String output, final boolean hasOutput) {
        return new CapturedElementData(null, CapturedData.text(output), false, false, hasOutput, null);
    }

    /**
     * One part, {@code records} records. e1 always has output; e2 has output only on even records.
     */
    private StepDataStore singlePart(final Path tempDir, final int records) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        for (int r = 0; r < records; r++) {
            store.putElementData(new StepLocation(META, 0, r), new ElementId(E1), "fp1", ed("e1r" + r, true));
            store.putElementData(new StepLocation(META, 0, r), new ElementId(E2), "fp2", ed("e2r" + r, r % 2 == 0));
        }
        return store;
    }

    private StepDataStore twoParts(final Path tempDir) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        for (long part = 0; part <= 1; part++) {
            for (int r = 0; r < 2; r++) {
                store.putElementData(new StepLocation(META, part, r), new ElementId(E1), "fp1", ed("p" + part + "r" + r, true));
            }
        }
        return store;
    }

    private PipelineStepRequest req(final StepType type,
                                    final StepLocation ref,
                                    final Map<String, SteppingFilterSettings> filterMap) {
        return PipelineStepRequest.builder()
                .stepType(type)
                .stepLocation(ref)
                .stepFilterMap(filterMap)
                .build();
    }

    private StepLocation loc(final long part, final long record) {
        return new StepLocation(META, part, record);
    }

    private StoreStepResolver.CapturedRange range(final long first, final long last) {
        return new StoreStepResolver.CapturedRange() {
            @Override
            public long first(final long partIndex) {
                return first;
            }

            @Override
            public long last(final long partIndex) {
                return last;
            }
        };
    }

    @Test
    void testNavigationBoundedByCapturedRange(@TempDir final Path tempDir) {
        // The store holds records 0..4, but the (reprocess) sweep has only captured 0..2 so far.
        final StepDataStore store = singlePart(tempDir, 5);
        final StoreStepResolver.CapturedRange range = range(0, 2);

        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FIRST, null, null), range)
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 0));
        // LAST is the last CAPTURED record (2), not the store's last (4).
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.LAST, null, null), range)
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 2));
        // FORWARD within the captured range advances.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 1), null), range)
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 2));
        // FORWARD from the frontier waits (empty) even though record 3 exists in the store.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 2), null), range))
                .isEmpty();
        // REFRESH of a record beyond the frontier is not yet available.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.REFRESH, loc(0, 3), null), range))
                .isEmpty();
    }

    @Test
    void testEmptyCapturedRangeResolvesNothing(@TempDir final Path tempDir) {
        // Nothing captured by the sweep yet, though the store is full - every step waits.
        final StepDataStore store = singlePart(tempDir, 5);
        final StoreStepResolver.CapturedRange none = range(-1, -1);

        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FIRST, null, null), none)).isEmpty();
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.LAST, null, null), none)).isEmpty();
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 0), null), none))
                .isEmpty();
    }

    @Test
    void testResolveSurfacesStoredSourceHighlight(@TempDir final Path tempDir) {
        // Build a store via putRecord WITH a per-record source-location snapshot (the resolver-facing helpers
        // above use putElementData, which stores no snapshot, so the served location has no highlight).
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        for (int r = 0; r < 3; r++) {
            final SourceLocation sl = SourceLocation.builder(META)
                    .withPartIndex(0L)
                    .withRecordIndex((long) r)
                    .withHighlight(new TextRange(new DefaultLocation(10 + r, 1), new DefaultLocation(10 + r, 40)))
                    .build();
            store.putRecord(new StepLocation(META, 0, r),
                    List.of(new StepDataStore.ElementRecord(new ElementId(E1), "fp1", ed("e1r" + r, true))),
                    sl);
        }

        final ResolvedStep step = resolver.resolve(store, META, fingerprints, req(StepType.FIRST, null, null))
                .orElseThrow();
        final SourceLocation served = step.stepData().getSourceLocation();

        // The served (part, record) coordinates come from the resolved step; the highlight is the one captured
        // for that record - previously dropped by assemble().
        assertThat(served.getPartIndex()).isZero();
        assertThat(served.getRecordIndex()).isZero();
        assertThat(served.getFirstHighlight().getLocationFrom().getLineNo()).isEqualTo(10);
        assertThat(served.getFirstHighlight().getLocationTo().getColNo()).isEqualTo(40);
    }

    @Test
    void testFirst(@TempDir final Path tempDir) {
        final ResolvedStep step = resolver.resolve(singlePart(tempDir, 5), META, fingerprints, req(StepType.FIRST, null, null))
                .orElseThrow();
        assertThat(step.foundLocation()).isEqualTo(loc(0, 0));
        assertThat(step.stepData().getElementMap().get(E1).getOutput()).isEqualTo("e1r0");
        assertThat(step.stepData().getElementMap().get(E2).getOutput()).isEqualTo("e2r0");
        assertThat(step.stepData().getSourceLocation().getPartIndex()).isZero();
        assertThat(step.stepData().getSourceLocation().getRecordIndex()).isZero();
    }

    @Test
    void testLast(@TempDir final Path tempDir) {
        final ResolvedStep step = resolver.resolve(singlePart(tempDir, 5), META, fingerprints, req(StepType.LAST, null, null))
                .orElseThrow();
        assertThat(step.foundLocation()).isEqualTo(loc(0, 4));
        assertThat(step.stepData().getElementMap().get(E1).getOutput()).isEqualTo("e1r4");
    }

    @Test
    void testForwardAndBackward(@TempDir final Path tempDir) {
        final StepDataStore store = singlePart(tempDir, 5);
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 2), null))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 3));
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.BACKWARD, loc(0, 2), null))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 1));
    }

    @Test
    void testRefresh(@TempDir final Path tempDir) {
        final StepDataStore store = singlePart(tempDir, 5);
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.REFRESH, loc(0, 2), null))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 2));
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.REFRESH, loc(0, 9), null)))
                .isEmpty();
    }

    @Test
    void testForwardPastEndAndBackwardBeforeStart(@TempDir final Path tempDir) {
        final StepDataStore store = singlePart(tempDir, 5);
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 4), null))).isEmpty();
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.BACKWARD, loc(0, 0), null))).isEmpty();
    }

    @Test
    void testEmptyStore(@TempDir final Path tempDir) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FIRST, null, null))).isEmpty();
    }

    @Test
    void testCrossesPartBoundary(@TempDir final Path tempDir) {
        final StepDataStore store = twoParts(tempDir);
        // Forward off the end of part 0 lands on the first record of part 1...
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 1), null))
                .orElseThrow().foundLocation()).isEqualTo(loc(1, 0));
        // ...and backward off the start of part 1 lands on the last record of part 0.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.BACKWARD, loc(1, 0), null))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 1));
        // LAST is the last record of the last part.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.LAST, null, null))
                .orElseThrow().foundLocation()).isEqualTo(loc(1, 1));
    }

    @Test
    void testFilterSkipsToMatchingRecord(@TempDir final Path tempDir) {
        final StepDataStore store = singlePart(tempDir, 5);
        // e2 only has output on even records; skip-to-NOT_EMPTY on e2.
        final Map<String, SteppingFilterSettings> filter = Map.of(
                E2, new SteppingFilterSettings(null, OutputState.NOT_EMPTY, null));

        // FIRST lands on record 0 (e2 has output there).
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FIRST, null, filter))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 0));
        // FORWARD from record 0 skips the odd (empty) record 1 to record 2.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 0), filter))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 2));
        // FORWARD from record 3 skips to record 4.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 3), filter))
                .orElseThrow().foundLocation()).isEqualTo(loc(0, 4));
        // No matching record after 4.
        assertThat(resolver.resolve(store, META, fingerprints, req(StepType.FORWARD, loc(0, 4), filter))).isEmpty();
    }

    @Test
    void testAssembleContainsOnlyElementsWithData(@TempDir final Path tempDir) {
        final StepDataStore store = singlePart(tempDir, 3);
        final Optional<ResolvedStep> step = resolver.resolve(store, META, fingerprints, req(StepType.FIRST, null, null));
        assertThat(step).isPresent();
        assertThat(step.get().stepData().getElementMap()).containsOnlyKeys(E1, E2);
    }
}
