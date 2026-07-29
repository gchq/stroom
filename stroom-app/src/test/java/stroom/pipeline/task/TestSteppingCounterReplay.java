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
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That a counting element resumes its count when a single record is replayed.
 * <p>
 * {@code IdEnrichmentFilter} numbers events across the whole stream, so its {@code EventId} is a function of
 * every record before the one being looked at - not of that record's input. Replaying record N on its own
 * therefore starts a fresh filter at zero and labels a mid-stream event as event 1, which is wrong in the
 * worst way: a plausible number, silently different from what a full sweep showed, on the field used to
 * identify an event uniquely within Stroom.
 * <p>
 * The fix stores each counting element's running total alongside the per-record shared scope and hands the
 * <em>previous</em> record's total back before a replay. This asserts the replayed {@code EventId} equals the
 * swept one, and - because "equal" is trivially satisfiable by two wrong values - that the swept one is not
 * the 1 a broken replay would produce.
 */
class TestSteppingCounterReplay extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String EDITED_ELEMENT_ID = "translationFilter";
    private static final String COUNTER_ELEMENT_ID = "idEnrichmentFilter";

    /** Mid-stream, so a replay that restarted the count would be visibly wrong. */
    private static final long MID_RECORD = 5;

    private static final Pattern EVENT_ID = Pattern.compile("EventId=\"(\\d+)\"");

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private XsltStore xsltStore;

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
    void aReplayedRecordKeepsTheEventIdTheSweepGaveIt() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final String editedXslt = xsltTextFor(pipelineDoc, EDITED_ELEMENT_ID);

        final PipelineStepRequest base = PipelineStepRequest.builder()
                .pipelineDoc(withIdEnrichment(pipelineDoc))
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED)
                        .addOperator(ExpressionOperator.builder().op(Op.OR)
                                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                                .build())
                        .build()))
                .timeout(Long.MAX_VALUE)
                .build();

        SteppingResult last = null;
        try {
            // LAST, not REFRESH: it waits for the stream to be fully captured, so the sweep is complete and
            // reusable before the edit. Otherwise the edit just sweeps the stream again and never replays.
            last = steppingService.step(base.copy().stepType(StepType.LAST).build());
            assertThat(last.isFoundRecord()).as("LAST found a record").isTrue();
            final StepLocation mid = new StepLocation(
                    last.getFoundLocation().getMetaId(), last.getFoundLocation().getPartIndex(), MID_RECORD);

            // What the sweep says. This is the truth the replay has to reproduce.
            final SteppingResult swept = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(mid)
                    .sessionUuid(last.getSessionUuid())
                    .build());
            assertThat(swept.isFoundRecord()).as("the sweep found record " + MID_RECORD).isTrue();
            final long sweptEventId = eventIdOf(swept, "swept");

            // The vacuity guard. If the swept id were 1, a replay that had lost the count entirely would
            // still match it and this test would assert nothing.
            assertThat(sweptEventId)
                    .as("a mid-stream record must have a count a restarted filter could not produce")
                    .isGreaterThan(1);

            final long reprocessBefore = steppingService.getReprocessLaunchCount();
            final long onDemandBefore = steppingService.getOnDemandLaunchCount();

            // Edit the element above the counter, then refresh the same record. The counter is downstream of
            // the edit, so it is re-run - for this record alone, with none of the records before it.
            final SteppingResult replayed = steppingService.step(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(mid)
                    .sessionUuid(swept.getSessionUuid())
                    .code(Map.of(EDITED_ELEMENT_ID, editedXslt))
                    .build());

            assertThat(steppingService.getOnDemandLaunchCount() + steppingService.getReprocessLaunchCount())
                    .as("the edit was answered by re-running the element, not from the completed sweep")
                    .isGreaterThan(onDemandBefore + reprocessBefore);
            assertThat(replayed.isFoundRecord()).as("the replay found record " + MID_RECORD).isTrue();

            assertThat(eventIdOf(replayed, "replayed"))
                    .as("the replayed record keeps the EventId the sweep gave it, rather than restarting at 1")
                    .isEqualTo(sweptEventId);
        } finally {
            if (last != null && last.getSessionUuid() != null) {
                steppingService.terminateStepping(
                        base.copy().sessionUuid(last.getSessionUuid()).build());
            }
        }
    }

    private long eventIdOf(final SteppingResult result, final String what) {
        final SharedElementData data = result.getStepData().getElementData(COUNTER_ELEMENT_ID);
        assertThat(data).as(what + " " + COUNTER_ELEMENT_ID + " was captured").isNotNull();
        final Matcher matcher = EVENT_ID.matcher(data.getOutput());
        assertThat(matcher.find())
                .as(what + " output carries an EventId attribute - it was: " + data.getOutput())
                .isTrue();
        return Long.parseLong(matcher.group(1));
    }

    /**
     * @return an in-memory copy of the feed's pipeline with an ID enrichment filter below the translation, so
     * there is a counting element to replay. No sample pipeline has one - they are used by indexing and
     * search extraction - which is why this behaviour had never been steppable.
     */
    private PipelineDoc withIdEnrichment(final PipelineDoc pipelineDoc) {
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addElement(PipelineDataUtil.createElement(
                COUNTER_ELEMENT_ID, "IdEnrichmentFilter", null, null));
        builder.addLink(PipelineDataUtil.createLink(EDITED_ELEMENT_ID, COUNTER_ELEMENT_ID));
        return pipelineDoc.copy().pipelineData(builder.build()).build();
    }

    /**
     * @return the text of an XSLT element's stylesheet, to inject back as "code" - which changes the
     * element's fingerprint exactly as typing would, while leaving its output identical.
     */
    private String xsltTextFor(final PipelineDoc pipelineDoc, final String elementId) {
        final PipelineData pipelineData = pipelineDoc.getPipelineData();
        for (final PipelineProperty property : pipelineData.getProperties().getAdd()) {
            if (elementId.equals(property.getElement()) && "xslt".equals(property.getName())) {
                return xsltStore.readDocument(property.getValue().getEntity()).getData();
            }
        }
        throw new IllegalStateException("No xslt property found for " + elementId);
    }
}
