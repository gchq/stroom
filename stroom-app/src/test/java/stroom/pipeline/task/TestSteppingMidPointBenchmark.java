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
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures what a mid-point step actually costs today, so the value of making it cheaper is a number rather
 * than an argument.
 * <p>
 * The scenario is the one users are in: a stream of thousands of records, sitting on a record somewhere in
 * the middle, editing an XSLT and refreshing to see the effect. Today each edit re-keys the element's
 * fingerprint and the whole of that element plus its downstream is re-run over the <b>entire stream</b> to
 * answer a question about <b>one record</b>. The design direction (see {@code stepping-design.md} §11) is to
 * materialise only the record being looked at; the gap this test prints is exactly what that would recover.
 * <p>
 * Three numbers are reported, and it is the ratios between them that matter:
 * <ul>
 *   <li><b>sweep</b> — the first capture of the stream. Paid once, and unavoidable.</li>
 *   <li><b>refresh (cached)</b> — a refresh with no edit, served straight from the store. The floor: this is
 *   what a step costs when nothing has to be computed.</li>
 *   <li><b>refresh (edited)</b> — a refresh after an edit, at the start, middle and end of the stream.</li>
 * </ul>
 * <p>
 * <b>Measured, 2,000 records:</b> sweep 2.1s; cached refresh 7ms; edited refresh 30ms at the first record,
 * ~670ms at the middle, ~1,120ms at the last. So the cost is <b>proportional to the record index, not to the
 * stream length</b> — the reprocess streams and the resolver returns as soon as it reaches the target record,
 * rather than waiting for the whole stream. (An earlier version of this comment predicted the cost would be
 * flat across positions. It is not; the measurement corrected it.)
 * <p>
 * That is the case for the §11 direction rather than against it: at ~0.55ms per record the wait is a third of
 * a second at record 500, but around a minute at record 100,000 — which is exactly the "editing the XSLT on
 * the 100,000th record" scenario. Materialising only the record being looked at replaces all of that with the
 * 7ms floor.
 * <p>
 * Record count is settable: {@code -Dstepping.benchmark.records=20000}. The default is deliberately modest so
 * this stays tolerable in a normal test run; the interesting numbers come from making it large.
 * <p>
 * There are no timing assertions. Wall-clock on a shared build machine is far too noisy to gate a build on,
 * and a flaky performance assertion is worse than none - it only teaches people to re-run the build. The
 * assertions here check the steps returned the right record; the timings are logged for a human to read.
 */
class TestSteppingMidPointBenchmark extends TranslationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSteppingMidPointBenchmark.class);

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String EDITED_ELEMENT_ID = "translationFilter";
    private static final int DEFAULT_RECORDS = 2_000;
    /** How many edit/refresh cycles to time at each position, to blunt the effect of one slow run. */
    private static final int CYCLES = 3;

    private int unresolvedSteps;

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

    /**
     * Scenario A's number is the full sweep's mid-point cost - the fallback mode, since
     * {@code stepping.skeletonSweep} became the default - so the benchmark pins skeleton off to keep
     * measuring the thing it was built to measure.
     */
    @BeforeEach
    void pinFullSweepMode() {
        setConfigValueMapper(stroom.pipeline.stepping.store.SteppingConfig.class,
                config -> config.withSkeletonSweep(false));
    }

    @AfterEach
    void unpinFullSweepMode() {
        clearConfigValueMapper();
    }

    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            importConfig();
            loadAllRefData();
            DONE_SETUP.set(true);
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    @Test
    void measureTheCostOfAMidPointStep() {
        testTranslationTask(FEED, false, false);

        final int recordCount = GeneratedEventStream.configuredRecordCount(DEFAULT_RECORDS);
        final long metaId = GeneratedEventStream.load(store, FEED, recordCount);

        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final String xsltText = xsltTextFor(pipelineDoc, EDITED_ELEMENT_ID);

        final PipelineStepRequest base = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                // Pinned to the generated stream, so nothing here depends on what else the feed holds.
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .timeout(Long.MAX_VALUE)
                .build();

        final List<String> report = new ArrayList<>();
        String sessionUuid = null;
        try {
            // 1) First capture of the stream. LAST waits for the sweep to complete, so this times the whole
            // capture and also tells us the real last record index.
            final long sweepStart = System.currentTimeMillis();
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            final long sweepMs = System.currentTimeMillis() - sweepStart;
            assertThat(last.isFoundRecord()).as("LAST found a record").isTrue();
            sessionUuid = last.getSessionUuid();

            final long partIndex = last.getFoundLocation().getPartIndex();
            final long lastRecord = last.getFoundLocation().getRecordIndex();
            report.add(String.format("records=%d  sweep=%,dms", recordCount, sweepMs));

            final long midRecord = lastRecord / 2;
            final long firstRecord = 0;

            // 2) The floor: a refresh with no edit, served from the store with nothing to compute.
            sessionUuid = timeAndReport(report, "refresh cached   @mid ", base, sessionUuid,
                    new StepLocation(metaId, partIndex, midRecord), null);

            // 3) The real cost: a refresh after an edit, at three positions. Each cycle injects DIFFERENT
            // code, because identical code is the same fingerprint and would simply be served from cache -
            // measuring nothing. This mirrors a user editing, refreshing, editing again.
            int edit = 0;
            for (final long record : List.of(firstRecord, midRecord, lastRecord)) {
                final String where = record == firstRecord
                        ? "@first"
                        : record == lastRecord ? "@last " : "@mid  ";
                for (int cycle = 0; cycle < CYCLES; cycle++) {
                    sessionUuid = timeAndReport(report, "refresh edited   " + where, base, sessionUuid,
                            new StepLocation(metaId, partIndex, record), editedXslt(xsltText, ++edit));
                }
            }
        } finally {
            if (sessionUuid != null) {
                steppingService.terminateStepping(base.copy().sessionUuid(sessionUuid).build());
            }
            if (unresolvedSteps > 0) {
                report.add(String.format(">>> %d step(s) did NOT resolve - see above", unresolvedSteps));
            }
            LOGGER.info(() -> "\n=== mid-point step cost ===\n" + String.join("\n", report) + "\n");
        }
    }

    /**
     * Run one REFRESH, time it, record it, and return the session id to carry into the next step.
     *
     * @param code the edited XSLT to inject, or null for an unedited refresh.
     */
    private String timeAndReport(final List<String> report,
                                 final String label,
                                 final PipelineStepRequest base,
                                 final String sessionUuid,
                                 final StepLocation location,
                                 final String code) {
        final PipelineStepRequest.Builder builder = base.copy()
                .stepType(StepType.REFRESH)
                .stepLocation(location)
                .sessionUuid(sessionUuid);
        if (code != null) {
            builder.code(Map.of(EDITED_ELEMENT_ID, code));
        }
        final PipelineStepRequest request = builder.build();

        final long start = System.currentTimeMillis();
        final SteppingResult result = steppingService.step(request);
        final long elapsed = System.currentTimeMillis() - start;

        if (!result.isFoundRecord()) {
            // Not an assertion: a step that fails to resolve is itself a finding, and aborting here would
            // throw away every measurement after it. Recorded loudly and the run continues.
            report.add(String.format("%s record %,7d  %,6dms  *** NOT RESOLVED  complete=%s errors=%s",
                    label, location.getRecordIndex(), elapsed, result.isComplete(), result.getGeneralErrors()));
            unresolvedSteps++;
            return result.getSessionUuid();
        }
        assertThat(result.getFoundLocation().getRecordIndex())
                .as(label + " landed on the record asked for").isEqualTo(location.getRecordIndex());
        assertThat(result.getStepData().getElementData(EDITED_ELEMENT_ID))
                .as(label + " served the edited element").isNotNull();

        report.add(String.format("%s record %,7d  %,6dms", label, location.getRecordIndex(), elapsed));
        return result.getSessionUuid();
    }

    /**
     * @return the XSLT with a distinct trailing comment, so every cycle is a genuinely different edit and
     * therefore a different fingerprint. Semantically identical, so the output stays comparable.
     */
    private String editedXslt(final String xsltText, final int edit) {
        final int close = xsltText.lastIndexOf("</xsl:stylesheet>");
        assertThat(close).as("the XSLT has a closing stylesheet tag").isGreaterThan(0);
        return xsltText.substring(0, close) + "<!-- benchmark edit " + edit + " -->" + xsltText.substring(close);
    }

    private String xsltTextFor(final PipelineDoc pipelineDoc, final String elementId) {
        final PipelineData merged = pipelineDataHolderFactory.create(pipelineDoc).getMergedPipelineData();
        DocRef xsltRef = null;
        for (final PipelineProperty property : merged.getAddedProperties()) {
            if (elementId.equals(property.getElement())
                && "xslt".equals(property.getName())
                && property.getValue() != null) {
                xsltRef = property.getValue().getEntity();
            }
        }
        assertThat(xsltRef).as(elementId + " references an XSLT").isNotNull();
        return xsltStore.readDocument(xsltRef).getData();
    }
}
