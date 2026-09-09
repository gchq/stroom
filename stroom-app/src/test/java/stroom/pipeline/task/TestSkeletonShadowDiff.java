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
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.SteppingService.SteppingCaptureResult;
import stroom.pipeline.stepping.read.StoreStepResolver;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shadow diff from {@code stepping-design.md} §11's appendix: the same generated stream served twice -
 * once by a synchronous whole-pipeline capture (the reference truth, skeleton mode never involved), once by
 * a skeleton-swept session materialising on demand - and every compared record's every element pane must be
 * byte-equal.
 * <p>
 * The golden corpus ({@code TestSkeletonSweptStepping}) already holds skeleton serving to the old engine's
 * recorded output across every feed and step type; what it cannot do is depth, because the sample streams
 * are ~100 records. This class walks a generated stream and - the part no walk reaches - REFRESHes records
 * deep in it, where a materialisation feeds off parser output thousands of records past anything else that
 * ever ran below the boundary.
 * <p>
 * <b>Coverage is bounded, not total</b>: the head walk plus sampled deep records, logged per run. Comparing
 * all records would cost one materialisation launch each; equality of the eager whole-extent path is instead
 * pinned by {@code TestReprocessFromStore} (byte-identity of a whole-stream reprocess) and the golden corpus.
 * The generated pipeline has no counting element, so strict equality is the correct demand - counter
 * divergence under a backbone is a separate, marked, decided case ({@code TestSteppingCounterReplay}).
 */
class TestSkeletonShadowDiff extends TranslationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSkeletonShadowDiff.class);

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final int RECORD_COUNT = 1_000;
    /** How many records the head walk serves. */
    private static final int WALK = 20;

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private Store store;

    private final StoreStepResolver resolver = new StoreStepResolver();

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

    @Test
    void skeletonSweptStreamServesByteIdenticalRecordsAtDepth() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final PipelineStepRequest base = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .timeout(Long.MAX_VALUE)
                .build();

        // The reference truth: the whole pipeline, synchronously, no skeleton anywhere near it.
        final SteppingCaptureResult truth = steppingService.capture(base, metaId);
        String sessionUuid = null;
        try {
            // The skeleton side, demand-shaped: eager would materialise everything in one run, which is the
            // path TestReprocessFromStore already proves - the point here is records materialised ALONE.
            setConfigValueMapper(SteppingConfig.class,
                    config -> config.withSkeletonSweep(true).withEagerMaterialisationRecords(0));

            final long fullSweepsBefore = steppingService.getFullSweepLaunchCount();
            final List<Long> compared = new ArrayList<>();

            // Head walk: FIRST then FORWARD, each record diffed against the truth capture.
            SteppingResult stepped = steppingService.step(base.copy().stepType(StepType.FIRST).build());
            sessionUuid = stepped.getSessionUuid();
            for (int i = 0; i < WALK && stepped.isFoundRecord(); i++) {
                diffAgainstTruth(truth, stepped, compared);
                stepped = steppingService.step(base.copy()
                        .stepType(StepType.FORWARD)
                        .stepLocation(stepped.getFoundLocation())
                        .sessionUuid(sessionUuid)
                        .build());
                sessionUuid = stepped.getSessionUuid();
            }

            // Deep records, each materialised in isolation - nothing below the boundary has ever run for
            // the records around them.
            for (final long deep : new long[]{RECORD_COUNT / 2, RECORD_COUNT - 2, RECORD_COUNT - 1}) {
                final SteppingResult refreshed = steppingService.step(base.copy()
                        .stepType(StepType.REFRESH)
                        .stepLocation(new StepLocation(metaId, 0, deep))
                        .sessionUuid(sessionUuid)
                        .build());
                sessionUuid = refreshed.getSessionUuid();
                assertThat(refreshed.isFoundRecord()).as("deep record " + deep + " resolved").isTrue();
                diffAgainstTruth(truth, refreshed, compared);
            }

            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("every compared record came off the backbone, never a full sweep")
                    .isEqualTo(fullSweepsBefore);
            assertThat(compared).hasSizeGreaterThanOrEqualTo(WALK + 3);
            LOGGER.info("Shadow diff: compared records {} of {} (bounded by design, not full coverage)",
                    compared, RECORD_COUNT);
        } finally {
            clearConfigValueMapper();
            if (sessionUuid != null) {
                steppingService.terminateStepping(base.copy().sessionUuid(sessionUuid).build());
            }
            steppingService.deleteCaptureSession(truth.sessionId());
        }
    }

    private void diffAgainstTruth(final SteppingCaptureResult truth,
                                  final SteppingResult served,
                                  final List<Long> compared) {
        final StepLocation loc = served.getFoundLocation();
        final SharedStepData truthData = resolver.resolve(
                        truth.store(), loc.getMetaId(), truth.fingerprints(),
                        PipelineStepRequest.builder()
                                .stepType(StepType.REFRESH)
                                .stepLocation(loc)
                                .build())
                .orElseThrow(() -> new AssertionError("truth capture has no record at " + loc))
                .stepData();

        final SharedStepData servedData = served.getStepData();
        assertThat(servedData.getElementMap().keySet())
                .as("same element set at " + loc)
                .isEqualTo(truthData.getElementMap().keySet());
        for (final String elementId : truthData.getElementMap().keySet()) {
            final SharedElementData truthElement = truthData.getElementData(elementId);
            final SharedElementData servedElement = servedData.getElementData(elementId);
            assertThat(servedElement.getInput())
                    .as("input of %s at %s", elementId, loc)
                    .isEqualTo(truthElement.getInput());
            assertThat(servedElement.getOutput())
                    .as("output of %s at %s", elementId, loc)
                    .isEqualTo(truthElement.getOutput());
        }
        compared.add(loc.getRecordIndex());
    }
}
