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

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scale scenarios from {@code stepping-design.md} §11 ("The scale scenarios"), as tests. The imagined
 * stream there is 10M records; the streams here are a few thousand, which is enough because every assertion is
 * about <b>which mechanism answered</b> (the launch counters), not about wall-clock.
 * <p>
 * Scenario A (refresh against a completed capture) is covered by {@code TestLiveReprocessOnEdit} and measured
 * by {@code TestSteppingMidPointBenchmark}. This class holds B and C:
 * <ul>
 *   <li><b>B</b> - a step ahead of the capture frontier waits for the sweep it already has, rather than
 *   launching another or discarding anything. Passes today.</li>
 *   <li><b>C</b> - an edit issued while the sweep is mid-flight should keep the partial upstream capture.
 *   <b>Fails today</b> - see the disabled test - and is the acceptance test for the lifetime-decoupling work:
 *   when it passes un-disabled, scenario C is fixed.</li>
 * </ul>
 * <p>
 * Both use a generated stream big enough that the sweep is still running when the second step is issued - the
 * step calls are milliseconds apart and the sweep takes seconds, so the "mid-flight" precondition holds by a
 * wide margin rather than by luck.
 */
class TestSteppingScaleScenarios extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String EDITED_ELEMENT_ID = "translationFilter";
    /** Big enough that a sweep takes seconds, so a step issued immediately after FIRST is genuinely
     * mid-sweep; small enough to stay tolerable in a normal run. */
    private static final int RECORD_COUNT = 4_000;

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
     * Scenario B: capture has reached record N; the user asks for a record far past N. The step must wait on
     * the sweep it already has - keeping everything captured so far and launching nothing new - and resolve
     * when the frontier passes the record. The wait itself is irreducible (the record's input cannot be known
     * without parsing everything before it); what this pins is that nothing is launched and nothing thrown
     * away.
     */
    @Test
    void aStepAheadOfTheCaptureFrontierWaitsForTheSweepItAlreadyHas() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);

        String sessionUuid = null;
        try {
            // FIRST resolves as soon as record 0 is captured, leaving the sweep filling the remaining
            // thousands of records in the background.
            final SteppingResult first = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = first.getSessionUuid();
            assertThat(first.isFoundRecord()).as("FIRST found a record").isTrue();

            final long fullSweeps = steppingService.getFullSweepLaunchCount();
            final long reprocesses = steppingService.getReprocessLaunchCount();
            final long onDemand = steppingService.getOnDemandLaunchCount();

            // Ask for the very last record while the sweep is still parsing towards it.
            final SteppingResult ahead = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, first.getFoundLocation().getPartIndex(),
                            RECORD_COUNT - 1))
                    .sessionUuid(sessionUuid)
                    .build());
            sessionUuid = ahead.getSessionUuid();

            assertThat(ahead.isFoundRecord())
                    .as("the step ahead of the frontier resolved once capture reached it").isTrue();
            assertThat(ahead.getFoundLocation().getRecordIndex()).isEqualTo(RECORD_COUNT - 1);
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("it waited on the sweep it already had - no second sweep").isEqualTo(fullSweeps);
            assertThat(steppingService.getReprocessLaunchCount()).isEqualTo(reprocesses);
            assertThat(steppingService.getOnDemandLaunchCount())
                    .as("and launched nothing else either").isEqualTo(onDemand);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /**
     * Scenario C, the behind-the-frontier half (build-order stage 2): capture has reached record N; the user
     * edits the XSLT and refreshes a record <b>behind</b> N. The parser's output for that record is already
     * on disk and the edit does not invalidate it, so the answer must be a reprocess fed from the partial
     * capture - not a full re-sweep of everything already parsed.
     * <p>
     * The un-edited REFRESH first is what pins "behind the frontier": it waits until capture passes the
     * record, so by the time the edit is issued the feed provably holds it - while the sweep, thousands of
     * records from done, is still running.
     */
    @Test
    void anEditBehindTheFrontierOfARunningSweepDoesNotResweep() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);
        final String xsltText = xsltTextFor(EDITED_ELEMENT_ID);
        final long record = 100;

        String sessionUuid = null;
        try {
            final SteppingResult first = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = first.getSessionUuid();
            assertThat(first.isFoundRecord()).as("FIRST found a record").isTrue();
            final long partIndex = first.getFoundLocation().getPartIndex();

            // Un-edited: waits on the running sweep until the frontier passes the record. No new launch.
            final SteppingResult unedited = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, partIndex, record))
                    .sessionUuid(sessionUuid)
                    .build());
            sessionUuid = unedited.getSessionUuid();
            assertThat(unedited.isFoundRecord()).as("the frontier has passed record " + record).isTrue();

            final long fullSweeps = steppingService.getFullSweepLaunchCount();
            final long reprocesses = steppingService.getReprocessLaunchCount();

            // The edit, issued while the sweep is still parsing towards record 3,999.
            final SteppingResult edited = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, partIndex, record))
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, xsltText))
                    .build());
            sessionUuid = edited.getSessionUuid();

            assertThat(edited.isFoundRecord()).as("the edited refresh resolved").isTrue();
            assertThat(edited.getFoundLocation().getRecordIndex()).isEqualTo(record);
            assertThat(edited.getStepData().getElementData(EDITED_ELEMENT_ID))
                    .as("the edited element was served").isNotNull();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("fed from the partial capture - no full re-sweep of records already parsed")
                    .isEqualTo(fullSweeps);
            assertThat(steppingService.getReprocessLaunchCount())
                    .as("and the mechanism was a reprocess").isEqualTo(reprocesses + 1);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /**
     * Scenario C: capture has reached record N; the user edits the XSLT and refreshes. The parser's
     * fingerprint is unchanged by the edit, so the upstream capture is still valid and should be kept - only
     * the edited element and below need re-running. Asserted via the launch counter: answering the edit must
     * not cost a second full sweep from record 0.
     * <p>
     * Disabled because it asserts the <b>desired</b> behaviour and the deficiency is real. Run un-disabled on
     * 2026-07-28 it failed exactly as §11 describes - "expected: 1L but was: 2L": {@code sweepFor} abandons
     * the in-flight sweep on any signature change, {@code priorCompleteCapture} requires a <i>complete</i>
     * prior capture, so the edit relaunched a full sweep and re-parsed everything the abandoned one had
     * already captured. The step still resolves (correctness holds; the second assertion passed) - the cost
     * is the failure. This is the acceptance test for the lifetime-decoupling work: fix scenario C, remove
     * the annotation, and this must pass as written.
     */
    @org.junit.jupiter.api.Disabled("Scenario C is a known deficiency - an edit mid-sweep abandons the "
                                    + "partial upstream capture and re-sweeps from record 0 (observed: "
                                    + "expected 1 full sweep but was 2). See stepping-design.md §11 'The "
                                    + "scale scenarios'. Enable when lifetime decoupling is built.")
    @Test
    void anEditIssuedMidSweepKeepsThePartialUpstreamCapture() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);
        final String xsltText = xsltTextFor(EDITED_ELEMENT_ID);

        String sessionUuid = null;
        try {
            final SteppingResult first = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = first.getSessionUuid();
            assertThat(first.isFoundRecord()).as("FIRST found a record").isTrue();

            final long fullSweeps = steppingService.getFullSweepLaunchCount();

            // Mid-sweep, edit the XSLT (inject its own source - a new fingerprint, same output) and refresh
            // a mid-stream record.
            final SteppingResult edited = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, first.getFoundLocation().getPartIndex(),
                            RECORD_COUNT / 2))
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, xsltText))
                    .build());
            sessionUuid = edited.getSessionUuid();

            // Correctness holds today - the step resolves. The deficiency is what it cost.
            assertThat(edited.isFoundRecord()).as("the edited refresh resolved").isTrue();
            assertThat(edited.getFoundLocation().getRecordIndex()).isEqualTo(RECORD_COUNT / 2);

            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("the partial upstream capture was kept - the edit must not re-sweep from record 0")
                    .isEqualTo(fullSweeps);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /**
     * A filtered scan whose match lies beyond the first window ({@code filteredScanWindow} default 50): the
     * probe hits only record-no 100 (index 99), so the scan must materialise window 1, find nothing, move
     * its frontier and materialise window 2 - within one long-poll, driven by the resolver loop.
     * <p>
     * This could not work under the step-keyed sweep cache: every loop iteration got the <i>completed</i>
     * first-window sweep back from the cache, read "fully captured, no match" and gave up - the scan could
     * never advance past window one. The store-as-cache model fixes it structurally: each iteration
     * recomputes its window from the frontier, the finished window is satisfied from the store, and only
     * the next one launches. The launch counter pins the mechanism: at least two materialisations.
     */
    @Test
    void aFilteredScanAdvancesAcrossWindows() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);
        final String probeXslt = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:stroom="stroom">
                  <xsl:template match="/">
                    <Probe>
                      <xsl:if test="number(stroom:record-no()) = 100">
                        <Hit><xsl:value-of select="stroom:record-no()"/></Hit>
                      </xsl:if>
                    </Probe>
                  </xsl:template>
                </xsl:stylesheet>
                """;

        String sessionUuid = null;
        try {
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            sessionUuid = last.getSessionUuid();
            assertThat(last.isFoundRecord()).as("LAST completed the sweep").isTrue();
            final StepLocation record0 =
                    new StepLocation(metaId, last.getFoundLocation().getPartIndex(), 0);

            final long onDemandBefore = steppingService.getOnDemandLaunchCount();

            final SteppingResult found = steppingService.step(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(record0)
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, probeXslt))
                    .stepFilterMap(Map.of(EDITED_ELEMENT_ID, new stroom.pipeline.shared.stepping
                            .SteppingFilterSettings(null, stroom.util.shared.OutputState.NOT_EMPTY,
                            java.util.List.of())))
                    .build());
            sessionUuid = found.getSessionUuid();

            assertThat(found.isFoundRecord()).as("the scan found the match beyond the first window").isTrue();
            assertThat(found.getFoundLocation().getMetaId())
                    .as("in this stream, not by crossing into another").isEqualTo(metaId);
            assertThat(found.getFoundLocation().getRecordIndex())
                    .as("record-no 100 is index 99 - past the 50-record first window").isEqualTo(99);
            assertThat(steppingService.getOnDemandLaunchCount() - onDemandBefore)
                    .as("which took at least two windows of materialisation")
                    .isGreaterThanOrEqualTo(2);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    private PipelineStepRequest requestFor(final long metaId) {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
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

    private String xsltTextFor(final String elementId) {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineData merged = pipelineDataHolderFactory
                .create(pipelineStore.readDocument(pipelineRef)).getMergedPipelineData();
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
