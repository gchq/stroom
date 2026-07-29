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

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.capture.ReprocessDriver.RecordRange;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.ReprocessPlanner.Decision;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How far ahead a filtered scan materialises, and that the distance really comes from
 * {@link SteppingConfig#getFilteredScanWindow()}.
 * <p>
 * The wiring needs its own test because the config default is the same 50 the value had as a hard-coded
 * constant. Every test that goes through a whole step therefore passes identically whether the size is read
 * from config or still baked in, so only a test that <em>changes</em> the setting can tell the two apart.
 * <p>
 * The window is also what bounds the work a filter matching nothing can do, so its arithmetic - inclusive
 * ends, clamped at the ends of the stream, resumed from the frontier already in the store - is worth pinning
 * down directly rather than inferring from a step's landing.
 */
class TestFilteredScanWindow {

    private static final long META_ID = 77L;
    private static final long PART = 0L;
    private static final String ELEMENT = "translationFilter";
    private static final ElementId ELEMENT_ID = new ElementId(ELEMENT);
    private static final String FINGERPRINT = "fp-cumulative";

    private static final ElementFingerprints FINGERPRINTS =
            new ElementFingerprints(Map.of(ELEMENT, "fp-own"), Map.of(ELEMENT, FINGERPRINT));
    private static final Decision DECISION = Decision.reprocess(ELEMENT, "combinedParser");

    /**
     * A store holding {@code records} records of upstream output, i.e. a stream whose extent is known. The
     * element being scanned has nothing written for it yet unless {@code materialised} says otherwise, which
     * is the state a filtered scan starts from.
     */
    private StepDataStore storeWith(final Path tempDir, final int records, final long... materialised) {
        final StepDataStore store = new StepDataStore(
                tempDir.resolve(Long.toString(META_ID)), new SteppingConfig());
        final ElementId upstream = new ElementId("combinedParser");
        for (int r = 0; r < records; r++) {
            store.putRecord(new StepLocation(META_ID, PART, r),
                    List.of(new StepDataStore.ElementRecord(upstream, "fp-upstream", data("out" + r))), null);
        }
        for (final long record : materialised) {
            store.putElementData(new StepLocation(META_ID, PART, record), ELEMENT_ID, FINGERPRINT,
                    data("scanned" + record));
        }
        return store;
    }

    private CapturedElementData data(final String output) {
        return new CapturedElementData(
                CapturedData.text("in"), CapturedData.text(output), false, false, true, null);
    }

    private SteppingService serviceWithWindow(final int window) {
        // Only the config and the arguments matter to filteredWindowFor; nothing else is touched, so the rest
        // of the graph of collaborators is deliberately absent rather than mocked.
        return new SteppingService(
                null, null, null, null, null, null, null, null, null, null,
                new SteppingConfig(null, null, null, null, null, null, window, null, null),
                null, null);
    }

    private RecordRange window(final SteppingService service,
                               final StepDataStore store,
                               final StepType stepType,
                               final Long fromRecord) {
        return service.filteredWindowFor(
                PipelineStepRequest.builder()
                        .stepType(stepType)
                        .stepLocation(fromRecord == null
                                ? null
                                : new StepLocation(META_ID, PART, fromRecord))
                        .build(),
                null,
                DECISION,
                store,
                FINGERPRINTS,
                META_ID);
    }

    @Test
    void testTheWindowSizeComesFromConfig(@TempDir final Path tempDir) {
        // The point of the whole class: two services differing only in the config value must scan different
        // distances. If the size were still a constant, both would return the same range.
        final StepDataStore store = storeWith(tempDir, 1_000);

        final RecordRange small = window(serviceWithWindow(10), store, StepType.FORWARD, 0L);
        final RecordRange large = window(serviceWithWindow(200), store, StepType.FORWARD, 0L);

        assertThat(small).isEqualTo(new RecordRange(PART, 1, 10));
        assertThat(large).isEqualTo(new RecordRange(PART, 1, 200));
    }

    @Test
    void testTheDefaultIsTheValueTheConstantHad(@TempDir final Path tempDir) {
        // Promoting a constant to config must not change behaviour for anyone who does not set it.
        final StepDataStore store = storeWith(tempDir, 1_000);

        assertThat(window(new SteppingService(
                        null, null, null, null, null, null, null, null, null, null,
                        new SteppingConfig(), null, null),
                store, StepType.FORWARD, 0L))
                .as("the default window is still 50 records")
                .isEqualTo(new RecordRange(PART, 1, 50));
    }

    @Test
    void testTheWindowIsClampedToTheEndOfTheStream(@TempDir final Path tempDir) {
        // A window that ran past the last record would ask the driver to materialise records that do not
        // exist. Ends are inclusive, so a 20-record stream scanned from record 0 ends at 19.
        final StepDataStore store = storeWith(tempDir, 20);

        assertThat(window(serviceWithWindow(500), store, StepType.FORWARD, 0L))
                .isEqualTo(new RecordRange(PART, 1, 19));
    }

    @Test
    void testASecondPollResumesPastWhatIsAlreadyMaterialised(@TempDir final Path tempDir) {
        // The frontier is read back out of the store rather than remembered between polls - that is what keeps
        // the scan from needing session state, and what stops it re-scanning the window it just did.
        final StepDataStore store = storeWith(tempDir, 1_000, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertThat(window(serviceWithWindow(10), store, StepType.FORWARD, 0L))
                .as("carries on from record 10, not from the step's reference record")
                .isEqualTo(new RecordRange(PART, 11, 20));
    }

    @Test
    void testAScanRunsOutOfStreamRatherThanReturningAnEmptyWindow(@TempDir final Path tempDir) {
        // Null means "nothing left here"; the caller then crosses into the next stream. An empty or backwards
        // range would instead be handed to the driver as work.
        final StepDataStore store = storeWith(tempDir, 5, 1, 2, 3, 4);

        assertThat(window(serviceWithWindow(10), store, StepType.FORWARD, 0L))
                .as("every record past the reference is already materialised")
                .isNull();
    }

    @Test
    void testABackwardScanWindowsBackwards(@TempDir final Path tempDir) {
        // BACKWARD is the awkward direction: the range still has to read low-to-high for the driver, but it is
        // the START that moves, so an off-by-one here silently scans the wrong records.
        final StepDataStore store = storeWith(tempDir, 1_000);

        assertThat(window(serviceWithWindow(10), store, StepType.BACKWARD, 100L))
                .isEqualTo(new RecordRange(PART, 90, 99));
    }

    @Test
    void testABackwardScanIsClampedToTheStartOfTheStream(@TempDir final Path tempDir) {
        final StepDataStore store = storeWith(tempDir, 1_000);

        assertThat(window(serviceWithWindow(50), store, StepType.BACKWARD, 5L))
                .isEqualTo(new RecordRange(PART, 0, 4));
    }

    @Test
    void testAStepThatNamesNoRecordScansFromTheEndItStartsAt(@TempDir final Path tempDir) {
        final StepDataStore store = storeWith(tempDir, 1_000);
        final SteppingService service = serviceWithWindow(10);

        assertThat(window(service, store, StepType.FIRST, null))
                .as("FIRST scans forwards from the start of the stream")
                .isEqualTo(new RecordRange(PART, 0, 9));
        assertThat(window(service, store, StepType.LAST, null))
                .as("LAST scans backwards from the end of it")
                .isEqualTo(new RecordRange(PART, 990, 999));
    }

    @Test
    void testARefreshIsNotAScan(@TempDir final Path tempDir) {
        // REFRESH names the record it wants, so there is nothing to search for and no window to open.
        final StepDataStore store = storeWith(tempDir, 1_000);

        assertThat(window(serviceWithWindow(10), store, StepType.REFRESH, 100L)).isNull();
    }
}
