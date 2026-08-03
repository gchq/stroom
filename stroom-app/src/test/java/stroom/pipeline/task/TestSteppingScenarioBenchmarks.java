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

package stroom.pipeline.task;

import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.OutputState;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmarks for scenarios C and D of {@code stepping-design.md} §11 ("The scale scenarios"). Scenario A is
 * measured by {@code TestSteppingMidPointBenchmark}; B has no benchmark because its cost <i>is</i> the sweep.
 * <p>
 * Like that class: no timing assertions (wall-clock on a shared build machine is too noisy to gate a build
 * on), correctness asserted, timings logged for a human. Record count via
 * {@code STEPPING_BENCHMARK_RECORDS=20000}.
 * <ul>
 *   <li><b>C</b> - what an edit issued <b>mid-sweep</b> costs against the same edit issued after the sweep
 *   completes. Today the mid-sweep edit abandons the partial capture and re-sweeps, so its cost includes
 *   re-parsing everything already captured; the post-completion edit is the on-demand replay at a few tens of
 *   ms. The gap between the two numbers is what the lifetime-decoupling work would recover, and after that
 *   work the first number should fall to roughly the second - this benchmark is the before/after gauge.</li>
 *   <li><b>D</b> - the ceiling on the skeleton-sweep idea: the same stream swept through the full pipeline
 *   and through one truncated at the record boundary (parser → record counter → split, everything below
 *   removed). The boundary-only sweep still parses every record and captures boundary IO - exactly the work a
 *   skeleton sweep cannot avoid - so the ratio between the two is the <b>most</b> a skeleton sweep could
 *   save. If the two are close, transforms and below-boundary capture are cheap and the idea is not worth
 *   building.</li>
 * </ul>
 */
class TestSteppingScenarioBenchmarks extends TranslationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSteppingScenarioBenchmarks.class);

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String EDITED_ELEMENT_ID = "translationFilter";
    private static final int DEFAULT_RECORDS = 4_000;

    /**
     * Everything strictly below the record boundary in the sample event pipeline
     * ({@code Common_Test_Event_Pipeline}): the chain is parser → readRecordCountFilter → splitFilter →
     * translationFilter → ... → streamAppender, and the boundary is the split. Removing these from a copy of
     * the pipeline leaves exactly what a skeleton sweep would run.
     */
    private static final List<PipelineElement> BELOW_BOUNDARY = List.of(
            PipelineDataUtil.createElement("translationFilter", "XSLTFilter", null, null),
            PipelineDataUtil.createElement("schemaFilter", "SchemaFilter", null, null),
            PipelineDataUtil.createElement("recordOutputFilter", "RecordOutputFilter", null, null),
            PipelineDataUtil.createElement("writeRecordCountFilter", "RecordCountFilter", null, null),
            PipelineDataUtil.createElement("xmlWriter", "XMLWriter", null, null),
            PipelineDataUtil.createElement("streamAppender", "StreamAppender", null, null));

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private PipelineDataHolderFactory pipelineDataHolderFactory;
    @Inject
    private Store store;

    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            importConfig();
            loadAllRefData();
            testTranslationTask(FEED, false, false);
            DONE_SETUP.set(true);
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    /**
     * Scenario C. Timeline: FIRST starts the sweep; an edited refresh at the mid record is issued immediately
     * (mid-sweep); then LAST completes capture under the edited signature; then the same edit-and-refresh is
     * repeated post-completion. The two "refresh edited" numbers are the same user action - only the state of
     * the capture differs.
     */
    @Test
    void measureAnEditIssuedMidSweep() {
        final int recordCount = GeneratedEventStream.configuredRecordCount(DEFAULT_RECORDS);
        final long metaId = GeneratedEventStream.load(store, FEED, recordCount);
        final PipelineStepRequest base = requestFor(metaId, feedPipeline());
        final String xsltText = xsltTextFor(EDITED_ELEMENT_ID);
        final long midRecord = recordCount / 2;

        final List<String> report = new ArrayList<>();
        report.add(String.format("records=%,d", recordCount));
        String sessionUuid = null;
        int edit = 0;
        try {
            // Start the sweep; resolves at record 0 while capture continues behind it.
            long start = System.currentTimeMillis();
            final SteppingResult first = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = first.getSessionUuid();
            assertThat(first.isFoundRecord()).as("FIRST found a record").isTrue();
            report.add(String.format("FIRST (sweep starts)            %,6dms", System.currentTimeMillis() - start));
            final long partIndex = first.getFoundLocation().getPartIndex();

            // The mid-sweep edit - scenario C's number. Today this abandons the partial capture and
            // re-parses from record 0 under the new signature.
            final long sweepsBefore = steppingService.getFullSweepLaunchCount();
            start = System.currentTimeMillis();
            SteppingResult result = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, partIndex, midRecord))
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, editedXslt(xsltText, ++edit)))
                    .build());
            sessionUuid = result.getSessionUuid();
            assertThat(result.isFoundRecord()).as("mid-sweep edited refresh resolved").isTrue();
            report.add(String.format("refresh edited MID-SWEEP  @mid  %,6dms   (full sweeps launched by it: %d)",
                    System.currentTimeMillis() - start,
                    steppingService.getFullSweepLaunchCount() - sweepsBefore));

            // Complete the capture under the edited signature, so the post-completion edits below reuse it.
            start = System.currentTimeMillis();
            result = steppingService.step(base.copy()
                    .stepType(StepType.LAST)
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, editedXslt(xsltText, edit)))
                    .build());
            sessionUuid = result.getSessionUuid();
            assertThat(result.isFoundRecord()).as("LAST found a record").isTrue();
            report.add(String.format("LAST (capture completes)        %,6dms", System.currentTimeMillis() - start));

            // The same action after completion - scenario A's route, the on-demand replay. Two cycles, each a
            // genuinely different edit (identical code would be the same fingerprint, served from cache).
            for (int cycle = 0; cycle < 2; cycle++) {
                start = System.currentTimeMillis();
                result = steppingService.step(base.copy()
                        .stepType(StepType.REFRESH)
                        .stepLocation(new StepLocation(metaId, partIndex, midRecord))
                        .sessionUuid(sessionUuid)
                        .code(Map.of(EDITED_ELEMENT_ID, editedXslt(xsltText, ++edit)))
                        .build());
                sessionUuid = result.getSessionUuid();
                assertThat(result.isFoundRecord()).as("post-completion edited refresh resolved").isTrue();
                report.add(String.format("refresh edited COMPLETE   @mid  %,6dms",
                        System.currentTimeMillis() - start));
            }
        } finally {
            terminate(base, sessionUuid);
            LOGGER.info(() -> "\n=== scenario C: edit issued mid-sweep vs after completion ===\n"
                              + String.join("\n", report) + "\n");
        }
    }

    /**
     * The eager small-stream policy's cost claim: a skeleton-swept small stream, materialised in full on the
     * first post-backbone demand, should cost about what one whole-pipeline sweep costs - the same parse and
     * the same transforms, meeting at the store instead of inside one SAX chain. LAST is the demand used
     * because it deterministically drives both paths to full extent. Two streams (same generated content) so
     * neither run reuses the other's capture. No asserts - the number is the point, and it justifies the
     * {@code eagerMaterialisationRecords} default.
     */
    @Test
    void measureTheEagerFirstPass() {
        final int recordCount = GeneratedEventStream.configuredRecordCount(DEFAULT_RECORDS);
        final List<String> report = new ArrayList<>();
        report.add(String.format("records=%,d", recordCount));

        // Full sweep (skeleton off), LAST as the first step.
        final long fullMetaId = GeneratedEventStream.load(store, FEED, recordCount);
        final PipelineStepRequest fullBase = requestFor(fullMetaId, feedPipeline());
        String sessionUuid = null;
        try {
            final long start = System.currentTimeMillis();
            final SteppingResult result =
                    steppingService.step(fullBase.copy().stepType(StepType.LAST).build());
            sessionUuid = result.getSessionUuid();
            assertThat(result.isFoundRecord()).isTrue();
            report.add(String.format("LAST via full sweep             %,6dms", System.currentTimeMillis() - start));
        } finally {
            terminate(fullBase, sessionUuid);
        }

        // Backbone + eager whole-extent materialisation (skeleton on, threshold at the stream size).
        final long eagerMetaId = GeneratedEventStream.load(store, FEED, recordCount);
        final PipelineStepRequest eagerBase = requestFor(eagerMetaId, feedPipeline());
        setConfigValueMapper(SteppingConfig.class,
                config -> config.withSkeletonSweep(true).withEagerMaterialisationRecords(recordCount));
        sessionUuid = null;
        try {
            final long start = System.currentTimeMillis();
            final SteppingResult result =
                    steppingService.step(eagerBase.copy().stepType(StepType.LAST).build());
            sessionUuid = result.getSessionUuid();
            assertThat(result.isFoundRecord()).isTrue();
            report.add(String.format("LAST via backbone + eager       %,6dms", System.currentTimeMillis() - start));
        } finally {
            clearConfigValueMapper();
            terminate(eagerBase, sessionUuid);
            LOGGER.info(() -> "\n=== eager small-stream policy: first pass vs full sweep ===\n"
                              + String.join("\n", report) + "\n");
        }
    }

    /**
     * Stage 5's acceptance: walking NEXT over a skeleton-swept stream should cost about what walking a fully
     * captured stream costs (the cached floor), because the prefetch window turns most steps into store
     * reads. Three walks of the same records: over a completed full sweep (the floor), over a skeleton
     * stream with prefetch, and over a skeleton stream without (the launch-per-step ceiling). No asserts -
     * the numbers are the point.
     */
    @Test
    void measureWalkingASkeletonSweptStream() {
        final int recordCount = GeneratedEventStream.configuredRecordCount(DEFAULT_RECORDS);
        final int walk = 30;
        final List<String> report = new ArrayList<>();
        report.add(String.format("records=%,d, walk=%d", recordCount, walk));

        // The cached floor: LAST completes a full sweep, then the walk is pure store reads.
        walkAndReport(report, "full sweep (cached floor)  ", recordCount, walk, null);
        // Skeleton, prefetch on (default window): most steps are store reads.
        walkAndReport(report, "skeleton + prefetch        ", recordCount, walk,
                config -> config.withSkeletonSweep(true).withEagerMaterialisationRecords(0));
        // Skeleton, prefetch off: a materialisation launch per step - what prefetch exists to avoid.
        walkAndReport(report, "skeleton, no prefetch      ", recordCount, walk,
                config -> config.withSkeletonSweep(true).withEagerMaterialisationRecords(0)
                        .withPrefetchWindow(1));

        LOGGER.info(() -> "\n=== walking NEXT over a skeleton-swept stream ===\n"
                          + String.join("\n", report) + "\n");
    }

    private void walkAndReport(final List<String> report,
                               final String label,
                               final int recordCount,
                               final int walk,
                               final java.util.function.UnaryOperator<SteppingConfig> configMapper) {
        final long metaId = GeneratedEventStream.load(store, FEED, recordCount);
        final PipelineStepRequest base = requestFor(metaId, feedPipeline());
        if (configMapper != null) {
            setConfigValueMapper(SteppingConfig.class, configMapper);
        }
        String sessionUuid = null;
        try {
            // FIRST establishes the session; for the floor run, LAST first so the sweep is complete and the
            // walk measures pure store reads.
            SteppingResult stepped;
            if (configMapper == null) {
                stepped = steppingService.step(base.copy().stepType(StepType.LAST).build());
                sessionUuid = stepped.getSessionUuid();
                stepped = steppingService.step(base.copy()
                        .stepType(StepType.FIRST).sessionUuid(sessionUuid).build());
            } else {
                stepped = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            }
            sessionUuid = stepped.getSessionUuid();
            assertThat(stepped.isFoundRecord()).isTrue();

            final long onDemandBefore = steppingService.getOnDemandLaunchCount();
            final long start = System.currentTimeMillis();
            for (int i = 0; i < walk; i++) {
                stepped = steppingService.step(base.copy()
                        .stepType(StepType.FORWARD)
                        .stepLocation(stepped.getFoundLocation())
                        .sessionUuid(sessionUuid)
                        .build());
                sessionUuid = stepped.getSessionUuid();
                assertThat(stepped.isFoundRecord()).isTrue();
            }
            final long elapsed = System.currentTimeMillis() - start;
            report.add(String.format("%s %,6dms  (%.1fms/step, %d launches)",
                    label, elapsed, elapsed / (double) walk,
                    steppingService.getOnDemandLaunchCount() - onDemandBefore));
        } finally {
            clearConfigValueMapper();
            terminate(base, sessionUuid);
        }
    }

    /**
     * Stage 5's open question - the "adaptive filtered window": is the fixed {@code filteredScanWindow}
     * leaving enough on the table to be worth replacing with a window that grows as a scan comes up empty?
     * <p>
     * The trade is one-dimensional. Each window is one materialisation launch, so a <b>small</b> window pays
     * the launch overhead many times over on the way to a distant match, while a <b>large</b> window
     * materialises records a near match never needed (~1ms each of below-boundary work). This measures both
     * ends - a hit at record-no 5 and a hit at the last record - at several fixed sizes over the same
     * stream. If the default's numbers sit close to the best size at both ends, an adaptive window has
     * nothing to buy and stays unbuilt; if each end wants a different size, growth is worth building.
     * <p>
     * Every run's probe bakes the window size and hit record into the code, so every run scans under its own
     * fingerprint - a run finding a previous run's materialised records would measure store reads, not
     * scanning.
     */
    @Test
    void measureTheFilteredScanWindowTradeOff() {
        final int recordCount = GeneratedEventStream.configuredRecordCount(DEFAULT_RECORDS);
        final long metaId = GeneratedEventStream.load(store, FEED, recordCount);
        final PipelineStepRequest base = requestFor(metaId, feedPipeline());
        final int[] windows = {10, 50, 200, 1_000};
        final long nearRecordNo = 5;

        final List<String> report = new ArrayList<>();
        report.add(String.format("records=%,d, default window=%d",
                recordCount, new SteppingConfig().getFilteredScanWindow()));
        String sessionUuid = null;
        try {
            // Complete the capture first: the scan under test materialises the EDITED element from the
            // parser feed already in the store, which is the shape the window exists for.
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            sessionUuid = last.getSessionUuid();
            assertThat(last.isFoundRecord()).as("LAST completed the sweep").isTrue();
            final StepLocation record0 =
                    new StepLocation(metaId, last.getFoundLocation().getPartIndex(), 0);

            for (final int window : windows) {
                setConfigValueMapper(SteppingConfig.class, config -> config.withFilteredScanWindow(window));
                sessionUuid = timeFilteredScan(base, record0, sessionUuid, window, nearRecordNo, report);
                sessionUuid = timeFilteredScan(base, record0, sessionUuid, window, recordCount, report);
            }
        } finally {
            clearConfigValueMapper();
            terminate(base, sessionUuid);
            LOGGER.info(() -> "\n=== the filtered-scan window trade-off (adaptive-window decision) ===\n"
                              + String.join("\n", report) + "\n");
        }
    }

    /**
     * One filtered FORWARD from record 0 whose only match is at {@code hitRecordNo}, timed, with the
     * on-demand launches it took. The filter is output NOT_EMPTY against a probe that emits a child element
     * only for the hit record, so the scan must materialise its way to the hit to find it.
     */
    private String timeFilteredScan(final PipelineStepRequest base,
                                    final StepLocation record0,
                                    final String sessionUuid,
                                    final int window,
                                    final long hitRecordNo,
                                    final List<String> report) {
        final String probeXslt = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:stroom="stroom">
                  <xsl:template match="/">
                    <!-- window %d -->
                    <Probe>
                      <xsl:if test="number(stroom:record-no()) = %d">
                        <Hit><xsl:value-of select="stroom:record-no()"/></Hit>
                      </xsl:if>
                    </Probe>
                  </xsl:template>
                </xsl:stylesheet>
                """.formatted(window, hitRecordNo);

        final long onDemandBefore = steppingService.getOnDemandLaunchCount();
        final long start = System.currentTimeMillis();
        final SteppingResult found = steppingService.step(base.copy()
                .stepType(StepType.FORWARD)
                .stepLocation(record0)
                .sessionUuid(sessionUuid)
                .code(Map.of(EDITED_ELEMENT_ID, probeXslt))
                .stepFilterMap(Map.of(EDITED_ELEMENT_ID,
                        new SteppingFilterSettings(null, OutputState.NOT_EMPTY, List.of())))
                .build());
        final long elapsed = System.currentTimeMillis() - start;
        assertThat(found.isFoundRecord())
                .as("the scan found the hit at record-no " + hitRecordNo).isTrue();
        assertThat(found.getFoundLocation().getRecordIndex())
                .as("record-no %d is index %d", hitRecordNo, hitRecordNo - 1)
                .isEqualTo(hitRecordNo - 1);
        report.add(String.format("window %,5d  hit @%,5d  %,7dms  (%3d launches)",
                window, hitRecordNo, elapsed,
                steppingService.getOnDemandLaunchCount() - onDemandBefore));
        return found.getSessionUuid();
    }

    /**
     * Scenario D's ceiling. Sweeps the same stream twice in separate sessions: once through the full
     * pipeline, once through the boundary-only truncation. Both parse every record; only the second skips the
     * transforms and their capture.
     */
    @Test
    void measureTheSkeletonSweepCeiling() {
        final int recordCount = GeneratedEventStream.configuredRecordCount(DEFAULT_RECORDS);
        final long metaId = GeneratedEventStream.load(store, FEED, recordCount);

        final PipelineDoc fullDoc = feedPipeline();
        final PipelineDoc boundaryDoc = boundaryOnly(fullDoc);
        // Sanity: the truncation really removed the below-boundary elements from the merged pipeline - a
        // removal that silently failed to match would sweep the full pipeline twice and report a ratio of ~1.
        final PipelineData merged = pipelineDataHolderFactory.create(boundaryDoc).getMergedPipelineData();
        assertThat(merged.getAddedElements())
                .as("the boundary-only pipeline has no elements below the split")
                .noneMatch(element -> "translationFilter".equals(element.getId())
                                      || "streamAppender".equals(element.getId()));

        final List<String> report = new ArrayList<>();
        report.add(String.format("records=%,d", recordCount));

        final long fullMs = timeFullSweep(requestFor(metaId, fullDoc), "full pipeline", recordCount, report);
        final long boundaryMs =
                timeFullSweep(requestFor(metaId, boundaryDoc), "boundary only", recordCount, report);

        final double saved = fullMs == 0
                ? 0
                : 100.0 * (fullMs - boundaryMs) / fullMs;
        report.add(String.format(
                "skeleton ceiling: at most %.0f%% of sweep cost is below-boundary transforms + capture",
                saved));
        LOGGER.info(() -> "\n=== scenario D: skeleton-sweep ceiling ===\n" + String.join("\n", report) + "\n");
    }

    /**
     * LAST waits for the whole stream to be captured, so it times a complete sweep. Each call gets its own
     * session (no sessionUuid carried in), so the two sweeps cannot share a store.
     */
    private long timeFullSweep(final PipelineStepRequest base,
                               final String label,
                               final int recordCount,
                               final List<String> report) {
        String sessionUuid = null;
        try {
            final long start = System.currentTimeMillis();
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            final long elapsed = System.currentTimeMillis() - start;
            sessionUuid = last.getSessionUuid();

            assertThat(last.isFoundRecord()).as(label + " LAST found a record").isTrue();
            // The record boundary sits above the truncation, so both pipelines must split the stream into the
            // same records - if they do not, the two sweeps did different work and the ratio is meaningless.
            assertThat(last.getFoundLocation().getRecordIndex())
                    .as(label + " swept the same number of records").isEqualTo(recordCount - 1);

            report.add(String.format("sweep %-14s %,6dms", label, elapsed));
            return elapsed;
        } finally {
            terminate(base, sessionUuid);
        }
    }

    private PipelineDoc feedPipeline() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        return pipelineStore.readDocument(pipelineRef);
    }

    /**
     * @return a copy of the pipeline with everything below the record boundary removed - what a skeleton
     * sweep would run. Removal entries merge against the inherited elements, so nothing is written back.
     */
    private PipelineDoc boundaryOnly(final PipelineDoc pipelineDoc) {
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.removeElements(BELOW_BOUNDARY);
        return pipelineDoc.copy().pipelineData(builder.build()).build();
    }

    private PipelineStepRequest requestFor(final long metaId, final PipelineDoc pipelineDoc) {
        return PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .timeout(Long.MAX_VALUE)
                .build();
    }

    private void terminate(final PipelineStepRequest base, final String sessionUuid) {
        if (sessionUuid != null) {
            steppingService.terminateStepping(base.copy().sessionUuid(sessionUuid).build());
        }
    }

    private String editedXslt(final String xsltText, final int edit) {
        final int close = xsltText.lastIndexOf("</xsl:stylesheet>");
        assertThat(close).as("the XSLT has a closing stylesheet tag").isGreaterThan(0);
        return xsltText.substring(0, close) + "<!-- benchmark edit " + edit + " -->" + xsltText.substring(close);
    }

    private String xsltTextFor(final String elementId) {
        final PipelineData merged = pipelineDataHolderFactory.create(feedPipeline()).getMergedPipelineData();
        for (final PipelineProperty property : merged.getAddedProperties()) {
            if (elementId.equals(property.getElement())
                && "xslt".equals(property.getName())
                && property.getValue() != null) {
                return xsltStore.readDocument(property.getValue().getEntity()).getData();
            }
        }
        throw new IllegalStateException("No xslt property found for " + elementId);
    }
}
