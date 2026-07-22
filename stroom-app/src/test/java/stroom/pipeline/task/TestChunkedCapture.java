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
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.SteppingService.SteppingCaptureResult;
import stroom.pipeline.stepping.read.StoreStepResolver;
import stroom.pipeline.stepping.read.StoreStepResolver.ResolvedStep;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks capture against serving: capturing a whole stream up front and then resolving a record from the
 * store must produce, for that same record, exactly what {@code SteppingService.step()} returns - input,
 * output and indicators, per element - over real sample feeds.
 * <p>
 * <b>Both sides are served from the store</b>, so this does not hold the engine to an independent
 * implementation - the golden {@code ~STEPPING~} corpus in {@link TestFullTranslationTaskAndStepping} is
 * what does that. What this checks is that the two ways in agree: the synchronous whole-stream
 * {@code capture()} and the lazily-swept session must produce the same IO for the same record, across
 * several feed types including a reader/text pipeline with no XML parser.
 */
class TestChunkedCapture extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;

    private final StoreStepResolver resolver = new StoreStepResolver();

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
    void testXmlEvents() {
        captureMatchesStepService("XML-EVENTS");
    }

    @Test
    void testDataSplitterEvents() {
        captureMatchesStepService("DATA_SPLITTER-EVENTS");
    }

    @Test
    void testJsonEvents() {
        captureMatchesStepService("JSON-EVENTS");
    }

    @Test
    void testRawStreamingEvents() {
        // Reader/text pipeline (no XML parser) - exercises the reader record detector.
        captureMatchesStepService("RAW_STREAMING-EVENTS");
    }

    private void captureMatchesStepService(final String feedName) {
        // Load the feed's raw source data.
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
        final PipelineStepRequest baseRequest = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(expression))
                .timeout(Long.MAX_VALUE)
                .build();

        // Step to the first record to discover which stream the selection starts in.
        final SteppingResult first = steppingService.step(baseRequest.copy().stepType(StepType.FIRST).build());
        assertThat(first.isFoundRecord()).as("FIRST found a record for " + feedName).isTrue();
        final long metaId = first.getFoundLocation().getMetaId();

        // Capture that whole stream in one pass.
        final SteppingCaptureResult capture = steppingService.capture(baseRequest, metaId);
        try {
            int compared = 0;
            SteppingResult stepped = first;
            // Walk every record step() visits within this stream and compare it to the capture.
            while (stepped.isFoundRecord() && stepped.getFoundLocation().getMetaId() == metaId) {
                final StepLocation loc = stepped.getFoundLocation();

                final PipelineStepRequest refresh = baseRequest.copy()
                        .stepType(StepType.REFRESH)
                        .stepLocation(loc)
                        .build();
                final Optional<ResolvedStep> resolved = resolver.resolve(
                        capture.store(), metaId, capture.fingerprints(), refresh);
                assertThat(resolved).as("captured record present at " + loc).isPresent();

                assertElementIoMatches(feedName, loc, stepped.getStepData(), resolved.get().stepData());
                compared++;

                stepped = steppingService.step(baseRequest.copy()
                        .stepType(StepType.FORWARD)
                        .stepLocation(loc)
                        .build());
            }
            assertThat(compared).as("compared at least one record for " + feedName).isGreaterThan(0);
        } finally {
            steppingService.deleteCaptureSession(capture.sessionId());
        }
    }

    private void assertElementIoMatches(final String feedName,
                                        final StepLocation loc,
                                        final SharedStepData stepped,
                                        final SharedStepData captured) {
        for (final String elementId : stepped.getElementMap().keySet()) {
            final SharedElementData steppedData = stepped.getElementData(elementId);
            final SharedElementData capturedData = captured.getElementMap().get(elementId);
            assertThat(capturedData)
                    .as("element %s captured at %s for %s", elementId, loc, feedName)
                    .isNotNull();
            assertThat(capturedData.getOutput())
                    .as("output for element %s at %s for %s", elementId, loc, feedName)
                    .isEqualTo(steppedData.getOutput());
            assertThat(capturedData.getInput())
                    .as("input for element %s at %s for %s", elementId, loc, feedName)
                    .isEqualTo(steppedData.getInput());
        }
    }
}
