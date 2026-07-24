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

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.SteppingService.SteppingCaptureResult;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ElementId;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reprocess de-risk: re-running an interior element from its <b>stored input</b> - without re-running the
 * pipeline above it - must produce output byte-identical to the full sweep's, over a real feed. This is the
 * load-bearing assumption of the split (Step 3): feed the edited element from stored upstream and re-run only
 * it and its downstream.
 * <p>
 * It captures the whole stream one way (sweep), reprocesses the XSLT element the other way (from the store),
 * and compares that element's captured output events record by record.
 */
class TestReprocessFromStore extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    // The XSLT (mutator) element in the sample XML-EVENTS pipeline, and the parser above it whose stored
    // output is the XSLT's input - the reusable upstream the reprocess is fed from.
    private static final String START_ELEMENT_ID = "translationFilter";
    private static final String FEED_ELEMENT_ID = "combinedParser";

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;

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
    void reprocessMatchesFullSweep() {
        final String feedName = "XML-EVENTS";
        testTranslationTask(feedName, false, false);

        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, feedName).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();
        final PipelineStepRequest request = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(expression))
                .timeout(Long.MAX_VALUE)
                .build();

        // Discover which stream the selection starts in.
        final SteppingResult first = steppingService.step(request.copy().stepType(StepType.FIRST).build());
        assertThat(first.isFoundRecord()).as("FIRST found a record for " + feedName).isTrue();
        final long metaId = first.getFoundLocation().getMetaId();

        // Full sweep of that whole stream.
        final SteppingCaptureResult sweep = steppingService.capture(request, metaId);
        SteppingCaptureResult reprocess = null;
        try {
            final StepDataStore sourceStore = sweep.store();
            final ElementFingerprints fingerprints = sweep.fingerprints();
            final String fingerprint = fingerprints.getCumulativeFingerprint(START_ELEMENT_ID);
            assertThat(fingerprint).as(START_ELEMENT_ID + " is a fingerprinted element").isNotNull();

            // Reprocess the XSLT element, fed from the parser's stored output, into a fresh store.
            assertThat(fingerprints.getCumulativeFingerprint(FEED_ELEMENT_ID))
                    .as(FEED_ELEMENT_ID + " is a fingerprinted element").isNotNull();
            reprocess = steppingService.reprocess(
                    request, metaId, START_ELEMENT_ID, FEED_ELEMENT_ID, sourceStore, fingerprints);
            final StepDataStore targetStore = reprocess.store();

            final ElementId startId = new ElementId(START_ELEMENT_ID);
            int comparedRecords = 0;
            for (final long partIndex : sourceStore.getPartIndices()) {
                final long firstRec = sourceStore.getFirstRecordIndex(partIndex);
                final long lastRec = sourceStore.getLastRecordIndex(partIndex);
                for (long r = firstRec; r <= lastRec; r++) {
                    final StepLocation loc = new StepLocation(metaId, partIndex, r);
                    final CapturedElementData swept =
                            sourceStore.getElementData(loc, startId, fingerprint).orElse(null);
                    final CapturedElementData reran =
                            targetStore.getElementData(loc, startId, fingerprint).orElse(null);
                    assertThat(swept).as("swept " + START_ELEMENT_ID + " at " + loc).isNotNull();
                    assertThat(reran).as("reprocessed " + START_ELEMENT_ID + " at " + loc).isNotNull();

                    // The load-bearing check: the reprocessed output events are byte-identical to the sweep's.
                    assertThat(reran.output()).as("output present at " + loc).isNotNull();
                    assertThat(swept.output()).as("swept output present at " + loc).isNotNull();
                    assertThat(Arrays.equals(reran.output().data(), swept.output().data()))
                            .as("reprocessed output == swept output at " + loc)
                            .isTrue();
                    assertThat(reran.hasOutput()).isEqualTo(swept.hasOutput());
                    comparedRecords++;
                }
            }
            assertThat(comparedRecords).as("some records were compared").isGreaterThan(0);
        } finally {
            steppingService.deleteCaptureSession(sweep.sessionId());
            if (reprocess != null) {
                steppingService.deleteCaptureSession(reprocess.sessionId());
            }
        }
    }
}
