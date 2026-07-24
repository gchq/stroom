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
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live routing of the split: editing a downstream element and stepping again must <b>route to a
 * reprocess</b> of just the changed element (fed from the parser's already-captured output) rather than a
 * second full sweep from source. The "edit" injects the XSLT element's own XSLT as {@code code}, which changes
 * its fingerprint (from no-injection to injected) so the planner sees a downstream edit.
 * <p>
 * It asserts both the launch <em>decision</em> (reprocess, not a second full sweep) and that the reprocessed
 * output is served correctly through {@code step()} for a {@code FIRST} step - the early-record case: the
 * resolver navigates within the reprocess sweep's own captured range, so it waits for record 0 to be
 * reprocessed rather than returning the reused upstream alone.
 */
class TestLiveReprocessOnEdit extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String START_ELEMENT_ID = "translationFilter";

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private PipelineDataHolderFactory pipelineDataHolderFactory;
    @Inject
    private XsltStore xsltStore;

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
    void editRoutesToReprocess() {
        testTranslationTask(FEED, false, false);

        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final String xsltText = xsltTextFor(pipelineDoc, START_ELEMENT_ID);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();
        final PipelineStepRequest base = PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(expression))
                .timeout(Long.MAX_VALUE)
                .build();

        final long reprocessBefore = steppingService.getReprocessLaunchCount();
        final long fullSweepBefore = steppingService.getFullSweepLaunchCount();

        SteppingResult second = null;
        try {
            // 1) LAST step, no code injection -> full sweep from source. LAST waits for its stream to be fully
            // captured (to know the true last record), so that stream's sweep is complete - and reusable -
            // before the edit. The selection may span several streams, so subsequent steps are pinned to this
            // stream (via a REFRESH reference) to reuse the sweep LAST just completed.
            final SteppingResult last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            assertThat(last.isFoundRecord()).as("LAST found a record").isTrue();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("first step was a full sweep").isEqualTo(fullSweepBefore + 1);
            assertThat(steppingService.getReprocessLaunchCount())
                    .as("first step did not reprocess").isEqualTo(reprocessBefore);

            // Record 0 of the completed stream - the early-record case.
            final StepLocation record0 = new StepLocation(
                    last.getFoundLocation().getMetaId(), last.getFoundLocation().getPartIndex(), 0);

            // 2) REFRESH record 0, still no code -> served from the cached completed sweep (no new launch).
            final SteppingResult first = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH).stepLocation(record0).sessionUuid(last.getSessionUuid()).build());
            assertThat(first.isFoundRecord()).as("REFRESH found record 0").isTrue();
            assertThat(first.getStepData().getElementData(START_ELEMENT_ID)).as(START_ELEMENT_ID + " captured")
                    .isNotNull();
            final String originalOutput = first.getStepData().getElementData(START_ELEMENT_ID).getOutput();
            assertThat(steppingService.getReprocessLaunchCount()).as("no reprocess yet").isEqualTo(reprocessBefore);

            // 3) REFRESH record 0, same session and stream, "edit" the XSLT (inject its own source) ->
            // fingerprint changes -> the planner routes to a reprocess. Record 0 is the early-record case the
            // readiness gate fixes: the resolver waits for the reprocess to write record 0 rather than return
            // the reused upstream alone. The served output must equal the original (the injected XSLT is its own).
            second = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(record0)
                    .sessionUuid(first.getSessionUuid())
                    .code(Map.of(START_ELEMENT_ID, xsltText))
                    .build());

            assertThat(steppingService.getReprocessLaunchCount())
                    .as("edit routed to a reprocess").isEqualTo(reprocessBefore + 1);
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("edit did not trigger a second full sweep").isEqualTo(fullSweepBefore + 1);
            assertThat(second.isFoundRecord()).as("REFRESH after edit found record 0").isTrue();
            assertThat(second.getStepData().getElementData(START_ELEMENT_ID))
                    .as("reprocessed " + START_ELEMENT_ID + " served (not just the reused upstream)").isNotNull();
            assertThat(second.getStepData().getElementData(START_ELEMENT_ID).getOutput())
                    .as("reprocessed output equals original").isEqualTo(originalOutput);
        } finally {
            if (second != null && second.getSessionUuid() != null) {
                steppingService.terminateStepping(base.copy().sessionUuid(second.getSessionUuid()).build());
            }
        }
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
