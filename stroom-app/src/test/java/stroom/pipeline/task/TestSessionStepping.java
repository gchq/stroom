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
import stroom.pipeline.stepping.StepResultResolver;
import stroom.pipeline.stepping.StepResultResolver.SessionStepResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.SteppingSession;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the Phase 3 async + cross-stream path end-to-end: a durable {@link SteppingSession} that
 * sweeps streams lazily and asynchronously must, when driven through {@link StepResultResolver#resolveSession},
 * reproduce exactly what the legacy per-step {@code step()} returns as it walks FORWARD across every stream
 * of a multi-stream feed (and for LAST across streams).
 */
class TestSessionStepping extends TranslationTest {

    private static final long TIMEOUT_MS = 60_000L;
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
        sessionMatchesLegacy("XML-EVENTS");
    }

    @Test
    void testRawStreamingEvents() {
        sessionMatchesLegacy("RAW_STREAMING-EVENTS");
    }

    private void sessionMatchesLegacy(final String feedName) {
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

        final SteppingSession session = steppingService.createSession(baseRequest);
        try {
            // Walk the whole feed forward via legacy step() and, at every record, check the async session
            // resolve (REFRESH at the same location, and FORWARD to the next) agrees.
            SteppingResult legacy = steppingService.step(baseRequest.copy().stepType(StepType.FIRST).build());
            assertThat(legacy.isFoundRecord()).as("legacy FIRST for " + feedName).isTrue();

            int compared = 0;
            boolean crossedStreams = false;
            long firstMetaId = legacy.getFoundLocation().getMetaId();

            while (legacy.isFoundRecord()) {
                final StepLocation loc = legacy.getFoundLocation();
                if (loc.getMetaId() != firstMetaId) {
                    crossedStreams = true;
                }

                final SessionStepResult refreshed = resolver.resolveSession(
                        session, baseRequest.copy().stepType(StepType.REFRESH).stepLocation(loc).build(), TIMEOUT_MS);
                assertThat(refreshed.foundRecord()).as("session has record at " + loc).isTrue();
                assertThat(refreshed.foundLocation()).isEqualTo(loc);
                assertElementIoMatches(feedName, loc, legacy.getStepData(), refreshed.stepData());
                compared++;

                final SteppingResult nextLegacy = steppingService.step(
                        baseRequest.copy().stepType(StepType.FORWARD).stepLocation(loc).build());
                if (nextLegacy.isFoundRecord()) {
                    final SessionStepResult sessionForward = resolver.resolveSession(
                            session, baseRequest.copy().stepType(StepType.FORWARD).stepLocation(loc).build(), TIMEOUT_MS);
                    assertThat(sessionForward.foundLocation())
                            .as("session FORWARD from " + loc + " for " + feedName)
                            .isEqualTo(nextLegacy.getFoundLocation());
                }
                legacy = nextLegacy;
            }

            assertThat(compared).as("compared records for " + feedName).isGreaterThan(0);
            assertThat(crossedStreams).as("feed " + feedName + " should span multiple streams").isTrue();

            // LAST across streams must agree too.
            final SteppingResult legacyLast = steppingService.step(baseRequest.copy().stepType(StepType.LAST).build());
            final SessionStepResult sessionLast = resolver.resolveSession(
                    session, baseRequest.copy().stepType(StepType.LAST).build(), TIMEOUT_MS);
            assertThat(sessionLast.foundLocation())
                    .as("session LAST for " + feedName)
                    .isEqualTo(legacyLast.getFoundLocation());
        } finally {
            session.close();
        }
    }

    private void assertElementIoMatches(final String feedName,
                                        final StepLocation loc,
                                        final SharedStepData legacy,
                                        final SharedStepData session) {
        for (final String elementId : legacy.getElementMap().keySet()) {
            final SharedElementData legacyData = legacy.getElementData(elementId);
            final SharedElementData sessionData = session.getElementMap().get(elementId);
            assertThat(sessionData).as("element %s at %s for %s", elementId, loc, feedName).isNotNull();
            assertThat(sessionData.getOutput())
                    .as("output for element %s at %s for %s", elementId, loc, feedName)
                    .isEqualTo(legacyData.getOutput());
            assertThat(sessionData.getInput())
                    .as("input for element %s at %s for %s", elementId, loc, feedName)
                    .isEqualTo(legacyData.getInput());
        }
    }
}
