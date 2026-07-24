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
import stroom.pipeline.stepping.read.ReprocessPlanner.Decision;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;
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

class TestReprocessPlanner {

    private static final long META = 1L;

    private final ReprocessPlanner planner = new ReprocessPlanner();

    // Chain: parser (record boundary) -> xslt -> writer.
    private final List<PlannerElement> elements = List.of(
            new PlannerElement("parser", true),
            new PlannerElement("xslt", false),
            new PlannerElement("writer", false));
    private final Map<String, List<String>> parentsOf = Map.of(
            "xslt", List.of("parser"),
            "writer", List.of("xslt"));

    private ElementFingerprints fingerprints(final String parserFp, final String xsltFp, final String writerFp) {
        return new ElementFingerprints(
                Map.of("parser", parserFp, "xslt", xsltFp, "writer", writerFp),
                Map.of("parser", parserFp, "xslt", xsltFp, "writer", writerFp));
    }

    private CapturedElementData ed() {
        return new CapturedElementData(null, CapturedData.text("out"), false, false, true, null);
    }

    private StepDataStore storeWith(final Path tempDir, final String parserFp, final String xsltFp,
                                    final String writerFp) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("parser"), parserFp, ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("xslt"), xsltFp, ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("writer"), writerFp, ed());
        return store;
    }

    @Test
    void testFirstSweepFromEmptyStoreIsFullSweep(@TempDir final Path tempDir) {
        final StepDataStore empty = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        final Decision d = planner.plan(elements, parentsOf, empty, fingerprints("p1", "x1", "w1"));
        assertThat(d.fullSweep()).isTrue();
    }

    @Test
    void testNothingChangedIsFullSweep(@TempDir final Path tempDir) {
        // Nothing to reprocess (everything reusable) - there is nothing for a reprocess to do.
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final Decision d = planner.plan(elements, parentsOf, store, fingerprints("p1", "x1", "w1"));
        assertThat(d.fullSweep()).isTrue();
    }

    @Test
    void testXsltEditReprocessesXsltFedFromParser(@TempDir final Path tempDir) {
        // The XSLT was edited: its and the writer's cumulative fingerprints changed; the parser is reused.
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final Decision d = planner.plan(elements, parentsOf, store, fingerprints("p1", "x2", "w2"));

        assertThat(d.fullSweep()).isFalse();
        assertThat(d.startElementId()).isEqualTo("xslt");
        assertThat(d.feedElementId()).isEqualTo("parser");
    }

    @Test
    void testWriterEditReprocessesWriterFedFromXslt(@TempDir final Path tempDir) {
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final Decision d = planner.plan(elements, parentsOf, store, fingerprints("p1", "x1", "w2"));

        assertThat(d.fullSweep()).isFalse();
        assertThat(d.startElementId()).isEqualTo("writer");
        assertThat(d.feedElementId()).isEqualTo("xslt");
    }

    @Test
    void testParserEditIsFullSweep(@TempDir final Path tempDir) {
        // A change at the record boundary redefines records -> full recapture.
        final StepDataStore store = storeWith(tempDir, "p1", "x1", "w1");
        final Decision d = planner.plan(elements, parentsOf, store, fingerprints("p2", "x2", "w2"));
        assertThat(d.fullSweep()).isTrue();
    }

    @Test
    void testForkEntryFallsBackToFullSweep(@TempDir final Path tempDir) {
        // xslt has two upstream neighbours (a merge). An edit to it is not the single-parent fast path.
        final List<PlannerElement> forked = List.of(
                new PlannerElement("parserA", true),
                new PlannerElement("parserB", true),
                new PlannerElement("xslt", false));
        final Map<String, List<String>> forkParents = Map.of("xslt", List.of("parserA", "parserB"));
        final StepDataStore store = new StepDataStore(tempDir.resolve(String.valueOf(META)), new SteppingConfig());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("parserA"), "a1", ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("parserB"), "b1", ed());
        store.putElementData(new StepLocation(META, 0, 0), new ElementId("xslt"), "x1", ed());

        final Decision d = planner.plan(forked, forkParents, store,
                new ElementFingerprints(
                        Map.of("parserA", "a1", "parserB", "b1", "xslt", "x2"),
                        Map.of("parserA", "a1", "parserB", "b1", "xslt", "x2")));
        assertThat(d.fullSweep()).isTrue();
    }
}
