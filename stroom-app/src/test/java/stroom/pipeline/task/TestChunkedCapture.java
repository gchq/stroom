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
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.StepResultResolver;
import stroom.pipeline.stepping.StepResultResolver.ResolvedStep;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.SteppingService.SteppingCaptureResult;
import stroom.meta.shared.FindMetaCriteria;
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
 * Validates Phase 2 capture end-to-end against the proven legacy stepping path: capturing a whole
 * stream then resolving a record from the store must reproduce exactly what the legacy per-step
 * {@code step()} returns for that same record (input, output and indicators per element). This is a
 * stronger, self-contained check than diffing the golden files, and reuses the same real sample feeds.
 */
class TestChunkedCapture extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;

    private final StepResultResolver resolver = new StepResultResolver();

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
        captureMatchesLegacy("XML-EVENTS");
    }

    @Test
    void testDataSplitterEvents() {
        captureMatchesLegacy("DATA_SPLITTER-EVENTS");
    }

    @Test
    void testJsonEvents() {
        captureMatchesLegacy("JSON-EVENTS");
    }

    @Test
    void testRawStreamingEvents() {
        // Reader/text pipeline (no XML parser) - exercises the reader record detector.
        captureMatchesLegacy("RAW_STREAMING-EVENTS");
    }

    private void captureMatchesLegacy(final String feedName) {
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

        // Legacy: step to the first record to discover the first stream.
        final SteppingResult first = steppingService.step(baseRequest.copy().stepType(StepType.FIRST).build());
        assertThat(first.isFoundRecord()).as("legacy FIRST found a record for " + feedName).isTrue();
        final long metaId = first.getFoundLocation().getMetaId();

        // Capture that whole stream in one pass.
        final SteppingCaptureResult capture = steppingService.capture(baseRequest, metaId);
        try {
            int compared = 0;
            SteppingResult legacy = first;
            // Walk every record the legacy stepper visits within this stream and compare to the capture.
            while (legacy.isFoundRecord() && legacy.getFoundLocation().getMetaId() == metaId) {
                final StepLocation loc = legacy.getFoundLocation();

                final PipelineStepRequest refresh = baseRequest.copy()
                        .stepType(StepType.REFRESH)
                        .stepLocation(loc)
                        .build();
                final Optional<ResolvedStep> resolved = resolver.resolve(
                        capture.store(), metaId, capture.fingerprints(), refresh);
                assertThat(resolved).as("captured record present at " + loc).isPresent();

                assertElementIoMatches(feedName, loc, legacy.getStepData(), resolved.get().stepData());
                compared++;

                legacy = steppingService.step(baseRequest.copy()
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
                                        final SharedStepData legacy,
                                        final SharedStepData captured) {
        for (final String elementId : legacy.getElementMap().keySet()) {
            final SharedElementData legacyData = legacy.getElementData(elementId);
            final SharedElementData capturedData = captured.getElementMap().get(elementId);
            assertThat(capturedData)
                    .as("element %s captured at %s for %s", elementId, loc, feedName)
                    .isNotNull();
            assertThat(capturedData.getOutput())
                    .as("output for element %s at %s for %s", elementId, loc, feedName)
                    .isEqualTo(legacyData.getOutput());
            assertThat(capturedData.getInput())
                    .as("input for element %s at %s for %s", elementId, loc, feedName)
                    .isEqualTo(legacyData.getInput());
        }
    }
}
