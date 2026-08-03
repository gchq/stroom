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
 *   <li><b>C</b> - an edit issued while the sweep is mid-flight keeps the partial upstream capture, whether
 *   the record it asks about is behind the capture frontier or still ahead of it. Two tests, one per half;
 *   they are the acceptance tests for the lifetime-decoupling work.</li>
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
     * Scenario C: capture has reached record N; the user edits the XSLT and refreshes a record <b>ahead</b> of
     * N. The parser's fingerprint is unchanged by the edit, so the upstream capture is still valid and is
     * kept - only the edited element and below need re-running. Asserted via the launch counter: answering the
     * edit must not cost a second full sweep from record 0.
     * <p>
     * This is the harder half of C (its behind-the-frontier sibling is above): at the moment the edit is
     * issued, the record it asks about has not been captured yet, so there is nothing to reprocess from
     * <i>yet</i>. Two things make it work - the in-flight capture is no longer abandoned on an edit that
     * leaves its fingerprints intact, and a step whose records the capture has not reached waits on that
     * capture instead of starting a second one. When the frontier passes the record, the edited element is
     * materialised from it.
     */
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
     * The same edit, but followed by a <b>step</b> rather than a refresh. Navigation names no record - the
     * target has to be derived - but deriving "the record after this one" is arithmetic on what has been
     * captured so far, and works just as well against a capture still in flight. So this must be answered by
     * materialising that one record <b>now</b>, neither by capturing the stream a second time alongside the
     * first nor by waiting for the first to finish.
     * <p>
     * Worth its own test because the two paths diverge before the planner is ever consulted: a REFRESH names
     * its record and a FORWARD does not, and until the demand covered both, keeping the running capture (which
     * scenario C requires) meant an edit-then-step ran two full captures of one stream at once.
     * <p>
     * The launch counters alone cannot tell "materialised promptly" from "waited for the capture, then
     * materialised" - both launch nothing. The LAST that follows supplies the discriminator without a
     * hard-coded threshold: it genuinely must wait for the capture to finish, so if the step had waited too,
     * the step would be the slow one and LAST the fast one. Asserting step &lt; LAST is therefore an assertion
     * about <i>which mechanism answered</i>, self-calibrating to whatever the machine is doing.
     */
    @Test
    void anEditFollowedByAStepMidSweepDoesNotResweep() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);
        final String xsltText = xsltTextFor(EDITED_ELEMENT_ID);

        String sessionUuid = null;
        try {
            final SteppingResult first = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = first.getSessionUuid();
            assertThat(first.isFoundRecord()).as("FIRST found a record").isTrue();
            final StepLocation start = first.getFoundLocation();

            final long fullSweeps = steppingService.getFullSweepLaunchCount();
            final long onDemand = steppingService.getOnDemandLaunchCount();

            // Edit, then step forward - issued while the sweep is still thousands of records from done.
            final long steppedStart = System.currentTimeMillis();
            final SteppingResult stepped = steppingService.step(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(start)
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, xsltText))
                    .build());
            final long steppedMs = System.currentTimeMillis() - steppedStart;
            sessionUuid = stepped.getSessionUuid();

            assertThat(stepped.isFoundRecord()).as("the edited step resolved").isTrue();
            assertThat(stepped.getFoundLocation().getRecordIndex())
                    .as("on the record after the one it stepped from")
                    .isEqualTo(start.getRecordIndex() + 1);
            assertThat(stepped.getStepData().getElementData(EDITED_ELEMENT_ID))
                    .as("the edited element was served").isNotNull();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("no second capture of a stream already being captured").isEqualTo(fullSweeps);
            assertThat(steppingService.getOnDemandLaunchCount())
                    .as("it materialised the one record instead").isEqualTo(onDemand + 1);

            // LAST cannot be answered until the capture finishes - it asks where the stream ends - so it waits
            // for the very capture the step above did not wait for.
            final long lastStart = System.currentTimeMillis();
            final SteppingResult last = steppingService.step(base.copy()
                    .stepType(StepType.LAST)
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, xsltText))
                    .build());
            final long lastMs = System.currentTimeMillis() - lastStart;
            sessionUuid = last.getSessionUuid();

            assertThat(last.isFoundRecord()).as("LAST resolved once the capture completed").isTrue();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("and it waited for the running capture rather than starting one")
                    .isEqualTo(fullSweeps);
            assertThat(steppedMs)
                    .as("the step was served from the frontier, not by waiting for the capture "
                        + "(step %dms vs LAST %dms)", steppedMs, lastMs)
                    .isLessThan(lastMs);
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
     * the next one launches. The launch counter pins the mechanism: one materialisation per window, so two
     * for a hit in the second window - plus at most one boundary race, because the resolver plans before it
     * scans, so a match committing just as its window's producer finishes can see one further window
     * launched before the scan that finds it. The tight bound is what pins the attach rule - a window
     * recomputed mid-fill overlaps the running producer's demand and must wait on it, not launch an
     * overlapping copy (which is what happened when attaching required the producer to cover the whole
     * recomputed window: one launch per wakeup, hundreds per scan).
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
                    .as("one launch per window crossed, plus at most one boundary race")
                    .isBetween(2L, 3L);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /**
     * The other half of the windowed scan's cost model: a match <b>inside</b> the first window is served as
     * soon as its record materialises - while the window's producer is still running - for one launch,
     * with no whole-stream creep. Before the overlap-attach and completed-materialisation-re-plan fixes,
     * the scan crept the entire stream whatever the hit position (each wakeup launched a new overlapping
     * window, and each iteration's scan was bounded by the just-launched sweep's own empty coverage), so a
     * hit at record-no 3 cost the same ~600 launches as a hit at the last record.
     */
    @Test
    void aFilteredScanServesANearMatchWithOneLaunch() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);
        final String probeXslt = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:stroom="stroom">
                  <xsl:template match="/">
                    <Probe>
                      <xsl:if test="number(stroom:record-no()) = 3">
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

            assertThat(found.isFoundRecord()).as("the scan found the near match").isTrue();
            assertThat(found.getFoundLocation().getRecordIndex())
                    .as("record-no 3 is index 2 - inside the first window").isEqualTo(2);
            assertThat(steppingService.getOnDemandLaunchCount() - onDemandBefore)
                    .as("served from the first window's materialisation, one launch")
                    .isEqualTo(1);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /**
     * Scenario D, the mechanism: with the skeleton sweep enabled, the first capture of a stream is truncated
     * at the record boundary and every record the user visits is materialised below it on demand. The launch
     * counters pin that nothing ever falls back to a whole-pipeline sweep - one backbone, N materialisations,
     * zero full sweeps - and the served step carries the below-boundary element, which the backbone alone
     * could never supply.
     */
    @Test
    void aSkeletonSweptStreamServesStepsWithoutAFullSweep() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);

        // Threshold below the stream size: this is the LARGE-stream shape, where cost must follow what the
        // user looks at - demand-shaped materialisation, never the whole extent. (Its small-stream sibling
        // below is the negative control: same steps, threshold above the size, one eager pass instead.)
        // Prefetch window pinned to 3 so the walk proves BOTH halves of demand shaping: a step materialises
        // a window in its direction of travel, and the steps inside that window are store reads that launch
        // NOTHING - walking six records costs exactly two launches, not six.
        setConfigValueMapper(stroom.pipeline.stepping.store.SteppingConfig.class,
                config -> config.withSkeletonSweep(true)
                        .withEagerMaterialisationRecords(100)
                        .withPrefetchWindow(3));
        String sessionUuid = null;
        try {
            final long backbones = steppingService.getBackboneLaunchCount();
            final long fullSweeps = steppingService.getFullSweepLaunchCount();
            final long onDemand = steppingService.getOnDemandLaunchCount();

            final SteppingResult first = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = first.getSessionUuid();
            assertThat(first.isFoundRecord()).as("FIRST resolved on a skeleton-swept stream").isTrue();
            assertThat(first.getStepData().getElementData(EDITED_ELEMENT_ID))
                    .as("with the below-boundary element served - which only a materialisation can supply")
                    .isNotNull();

            // Walk five more records: two sit in FIRST's prefetch window (store reads), the sixth record
            // opens the next window, whose remaining records are store reads again.
            SteppingResult stepped = first;
            for (int i = 0; i < 5; i++) {
                stepped = steppingService.step(base.copy()
                        .stepType(StepType.FORWARD)
                        .stepLocation(stepped.getFoundLocation())
                        .sessionUuid(sessionUuid)
                        .build());
                sessionUuid = stepped.getSessionUuid();
                assertThat(stepped.isFoundRecord()).as("FORWARD " + (i + 1) + " resolved").isTrue();
            }
            assertThat(stepped.getFoundLocation().getRecordIndex())
                    .isEqualTo(first.getFoundLocation().getRecordIndex() + 5);

            assertThat(steppingService.getBackboneLaunchCount())
                    .as("one backbone captured the stream's shape").isEqualTo(backbones + 1);
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("and the whole pipeline was never swept").isEqualTo(fullSweeps);
            assertThat(steppingService.getOnDemandLaunchCount())
                    .as("six records walked = two prefetch windows of three, NOT six launches")
                    .isEqualTo(onDemand + 2);
        } finally {
            clearConfigValueMapper();
            terminate(base, sessionUuid);
        }
    }

    /**
     * The small-stream policy: once the backbone of a stream at or under the eager threshold has completed,
     * the next demand under the same (un-edited) code is promoted to the whole extent - one reprocess,
     * roughly the cost of the full sweep it replaced - and every later step is a store read, launching
     * nothing. The gate is the backbone's fingerprint signature, so an EDIT is never promoted however small
     * the stream: the post-edit refresh stays per-record, the inner loop this whole design exists to keep
     * fast.
     * <p>
     * Starts with LAST because LAST deterministically waits for the backbone to complete - a FIRST would be
     * served per-record behind the still-running backbone's frontier (scenario C machinery, correct but
     * racy for a counter test).
     */
    @Test
    void aSmallSkeletonStreamIsMaterialisedEagerlyExactlyOnce() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(metaId);
        final String xsltText = xsltTextFor(EDITED_ELEMENT_ID);

        // Threshold at the stream size: the 4,000-record stream counts as small.
        setConfigValueMapper(stroom.pipeline.stepping.store.SteppingConfig.class,
                config -> config.withSkeletonSweep(true).withEagerMaterialisationRecords(RECORD_COUNT));
        String sessionUuid = null;
        try {
            final long backbones = steppingService.getBackboneLaunchCount();
            final long fullSweeps = steppingService.getFullSweepLaunchCount();
            final long reprocesses = steppingService.getReprocessLaunchCount();
            final long onDemand = steppingService.getOnDemandLaunchCount();

            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            sessionUuid = last.getSessionUuid();
            assertThat(last.isFoundRecord()).as("LAST resolved").isTrue();
            assertThat(last.getFoundLocation().getRecordIndex()).isEqualTo(RECORD_COUNT - 1);

            // Walk back a few records: every one must be a store read - no further launches of any kind.
            SteppingResult stepped = last;
            for (int i = 0; i < 3; i++) {
                stepped = steppingService.step(base.copy()
                        .stepType(StepType.BACKWARD)
                        .stepLocation(stepped.getFoundLocation())
                        .sessionUuid(sessionUuid)
                        .build());
                sessionUuid = stepped.getSessionUuid();
                assertThat(stepped.isFoundRecord()).isTrue();
            }

            assertThat(steppingService.getBackboneLaunchCount()).isEqualTo(backbones + 1);
            assertThat(steppingService.getReprocessLaunchCount())
                    .as("one eager whole-extent materialisation").isEqualTo(reprocesses + 1);
            assertThat(steppingService.getOnDemandLaunchCount())
                    .as("and nothing per-record").isEqualTo(onDemand);
            assertThat(steppingService.getFullSweepLaunchCount()).isEqualTo(fullSweeps);

            // An edit on the same small stream: per-record, NOT eager - the edit changed the signature, and
            // only the backbone's own signature is ever promoted.
            final SteppingResult edited = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(stepped.getFoundLocation())
                    .sessionUuid(sessionUuid)
                    .code(Map.of(EDITED_ELEMENT_ID, xsltText))
                    .build());
            sessionUuid = edited.getSessionUuid();
            assertThat(edited.isFoundRecord()).as("the edited refresh resolved").isTrue();
            assertThat(steppingService.getOnDemandLaunchCount())
                    .as("the edit was materialised per-record").isEqualTo(onDemand + 1);
            assertThat(steppingService.getReprocessLaunchCount())
                    .as("not eagerly").isEqualTo(reprocesses + 2);
        } finally {
            clearConfigValueMapper();
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
