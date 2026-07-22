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

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;
import stroom.pipeline.stepping.read.StagePlanner.StagePlan;
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

class TestStagePlanner {

    private static final long META = 1L;

    private final StagePlanner planner = new StagePlanner();

    // Chain: parser (record boundary) -> xslt -> writer.
    private final List<PlannerElement> elements = List.of(
            new PlannerElement("parser", true),
            new PlannerElement("xslt", false),
            new PlannerElement("writer", false));

    private ElementFingerprints fingerprints(final String parserFp, final String xsltFp, final String writerFp) {
        return new ElementFingerprints(
                Map.of("parser", parserFp, "xslt", xsltFp, "writer", writerFp),
                Map.of("parser", parserFp, "xslt", xsltFp, "writer", writerFp));
    }

    private CapturedElementData ed() {
        return new CapturedElementData(null, CapturedData.text("out"), false, false, true, null);
    }

    /**
     * Populate the store with a chunk for each element at the given fingerprints (record 0).
     */
    private StepDataStore storeWith(final Path tempDir, final String parserFp, final String xsltFp, final String writerFp) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("parser"), parserFp, ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("xslt"), xsltFp, ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("writer"), writerFp, ed());
        return store;
    }

    @Test
    void testEverythingReusedWhenNothingChanged(@TempDir final Path tempDir) {
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final StagePlan plan = planner.plan(elements, store, fingerprints("p1", "x1", "w1"));

        assertThat(plan.fullRecapture()).isFalse();
        assertThat(plan.reuse()).containsExactlyInAnyOrder("parser", "xslt", "writer");
        assertThat(plan.reprocess()).isEmpty();
    }

    @Test
    void testDownstreamEditReprocessesOnlyChangedAndDownstream(@TempDir final Path tempDir) {
        // Prior store captured under p1/x1/w1. The xslt was edited so its (and the writer's) cumulative
        // fingerprints changed to x2/w2; the parser is unchanged.
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final StagePlan plan = planner.plan(elements, store, fingerprints("p1", "x2", "w2"));

        assertThat(plan.fullRecapture()).isFalse();
        assertThat(plan.reuse()).containsExactly("parser");
        assertThat(plan.reprocess()).containsExactlyInAnyOrder("xslt", "writer");
    }

    @Test
    void testRevertReusesEverythingAgain(@TempDir final Path tempDir) {
        // The store retains both the original and edited xslt/writer chunks (retain default >= 2), so
        // reverting to the original fingerprints reuses everything.
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        // The edited fingerprints are separate files, so their first record is index 0.
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("xslt"), "x2", ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("writer"), "w2", ed());

        final StagePlan reverted = planner.plan(elements, store, fingerprints("p1", "x1", "w1"));
        assertThat(reverted.fullRecapture()).isFalse();
        assertThat(reverted.reuse()).containsExactlyInAnyOrder("parser", "xslt", "writer");
        assertThat(reverted.reprocess()).isEmpty();
    }

    @Test
    void testParserChangeForcesFullRecapture(@TempDir final Path tempDir) {
        // Parser edited -> every cumulative fingerprint changes and the parser is at the record boundary.
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final StagePlan plan = planner.plan(elements, store, fingerprints("p2", "x2", "w2"));

        assertThat(plan.fullRecapture()).isTrue();
        assertThat(plan.reuse()).isEmpty();
        assertThat(plan.reprocess()).containsExactlyInAnyOrder("parser", "xslt", "writer");
    }

    @Test
    void testMissingFromEmptyStoreReprocessesAll(@TempDir final Path tempDir) {
        final StepDataStore emptyStore = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        final StagePlan plan = planner.plan(elements, emptyStore, fingerprints("p1", "x1", "w1"));

        // The parser (boundary) chunk is absent, so this is a full recapture.
        assertThat(plan.fullRecapture()).isTrue();
        assertThat(plan.reprocess()).containsExactlyInAnyOrder("parser", "xslt", "writer");
    }
}
