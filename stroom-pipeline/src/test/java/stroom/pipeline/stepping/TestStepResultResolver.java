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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.stepping.StepResultResolver.ResolvedStep;
import stroom.util.shared.ElementId;
import stroom.util.shared.OutputState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestStepResultResolver {

    private static final long META = 1L;
    private static final String E1 = "e1";
    private static final String E2 = "e2";

    private final StepResultResolver resolver = new StepResultResolver();
    // Active fingerprints: e1 -> fp1, e2 -> fp2. Own map is unused by the resolver.
    private final ElementFingerprints fingerprints = new ElementFingerprints(
            Map.of(E1, "o1", E2, "o2"),
            Map.of(E1, "fp1", E2, "fp2"));

    private SharedElementData ed(final String output, final boolean hasOutput) {
        return new SharedElementData(null, output, null, false, false, hasOutput);
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
