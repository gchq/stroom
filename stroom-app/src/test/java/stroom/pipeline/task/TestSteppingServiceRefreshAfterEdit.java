/*
 * Copyright 2026 Crown Copyright
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
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// The stepping screen's edit-then-refresh loop on the <b>first</b> record of a stream. After the XSLT is
/// changed - in the editor (sent as injected code) or by saving the document (an edit applied from the AI chat
/// and then saved leaves the editor clean, so no code is sent) - pressing Refresh must show the new output
/// straight away, not only once the user has stepped on to a record that had not been produced yet.
///
/// Every step is polled as the stepping screen polls it: a short timeout, asked again under the session id the
/// last answer gave until the step completes.
class TestSteppingServiceRefreshAfterEdit extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String EDITED_ELEMENT_ID = "translationFilter";
    private static final String MARKER = "EditedOutputMarker";
    private static final long CLIENT_POLL_TIMEOUT_MS = 40L;
    private static final long STEP_DEADLINE_MS = 60_000L;

    // A translation whose output can't be mistaken for the saved one.
    private static final String EDITED_XSLT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <%s/>
              </xsl:template>
            </xsl:stylesheet>
            """.formatted(MARKER);

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

    @AfterEach
    void tearDown() {
        clearConfigValueMapper();
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    /// The XSLT is edited in the stepping editor, so the client sends it as code for the element. Opening the
    /// stream either with the First button (FIRST, no location) or as the stepping screen opens a stream (a
    /// REFRESH of record 0).
    @ParameterizedTest(name = "skeletonSweep={0}, openWithRefresh={1}")
    @CsvSource({"true, false", "false, false", "true, true", "false, true"})
    void refreshOfFirstRecordAfterAnUnsavedEditShowsTheEdit(final boolean skeletonSweep,
                                                           final boolean openWithRefresh) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        SteppingResult latest = null;
        try {
            final SteppingResult opened = openWithRefresh
                    ? stepUntilComplete(base.copy()
                            .stepType(StepType.REFRESH)
                            .stepLocation(new StepLocation(firstMetaId(base), 0L, 0L))
                            .build())
                    : stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
            latest = opened;
            assertThat(opened.isFoundRecord()).as("opening step found a record").isTrue();
            final StepLocation found = opened.getFoundLocation();
            assertThat(outputOf(opened)).as("saved output").doesNotContain(MARKER);

            final SteppingResult refreshed = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(found)
                    .sessionUuid(opened.getSessionUuid())
                    .code(Map.of(EDITED_ELEMENT_ID, EDITED_XSLT))
                    .build());
            latest = refreshed;
            assertThat(refreshed.isFoundRecord()).as("REFRESH after the edit found a record").isTrue();
            assertThat(refreshed.getFoundLocation()).as("on the same record").isEqualTo(found);
            assertThat(outputOf(refreshed))
                    .as("REFRESH of the first record shows the edited XSLT's output")
                    .contains(MARKER);
        } finally {
            terminate(base, latest);
        }
    }

    /// The XSLT document is saved part way through the session (e.g. after applying a change proposed in the AI
    /// chat and saving it). The editor is then clean, so the client sends no code, exactly as before the edit.
    /// Refreshing the record already shown must show the saved code's output, as stepping on to the next record
    /// does.
    @ParameterizedTest(name = "skeletonSweep={0}")
    @ValueSource(booleans = {true, false})
    void refreshOfFirstRecordAfterSavingAnEditShowsTheSavedCode(final boolean skeletonSweep) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        final DocRef xsltRef = xsltRefFor(EDITED_ELEMENT_ID);
        final String originalXslt = xsltStore.readDocument(xsltRef).getData();
        SteppingResult latest = null;
        try {
            // Open the stream as the stepping screen does: a REFRESH of record 0.
            final SteppingResult opened = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(firstMetaId(base), 0L, 0L))
                    .build());
            latest = opened;
            assertThat(opened.isFoundRecord()).as("opening step found a record").isTrue();
            final StepLocation found = opened.getFoundLocation();
            assertThat(outputOf(opened)).as("output before the save").doesNotContain(MARKER);

            saveXslt(xsltRef, EDITED_XSLT);

            final SteppingResult refreshed = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(found)
                    .sessionUuid(opened.getSessionUuid())
                    .build());
            latest = refreshed;
            final SteppingResult forward = stepUntilComplete(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(found)
                    .sessionUuid(refreshed.getSessionUuid())
                    .build());
            latest = forward;

            final SoftAssertions softly = new SoftAssertions();
            softly.assertThat(refreshed.getFoundLocation()).as("REFRESH stays on the record").isEqualTo(found);
            softly.assertThat(outputOf(refreshed))
                    .as("REFRESH of the first record after the save shows the saved XSLT's output")
                    .contains(MARKER);
            softly.assertThat(outputOf(forward))
                    .as("FORWARD after the save shows the saved XSLT's output")
                    .contains(MARKER);
            softly.assertAll();
        } finally {
            terminate(base, latest);
            saveXslt(xsltRef, originalXslt);
        }
    }

    /// As [#refreshOfFirstRecordAfterSavingAnEditShowsTheSavedCode(boolean)], on a stream big enough that the
    /// skeleton sweep is still running when it is opened, so only record 0 is produced before the save. This is
    /// the shape reported from use: Refresh shows the output of the code as it was before the save, while
    /// stepping forward, to a record produced after the save, shows the saved code's output.
    @ParameterizedTest(name = "recordCount={0}")
    @ValueSource(ints = {4_000})
    void refreshOfFirstRecordAfterSavingAnEditMatchesTheNextRecord(final int recordCount) {
        pinSkeletonSweep(true);
        final long metaId = GeneratedEventStream.load(store, FEED, recordCount);
        final PipelineStepRequest base = baseRequest().copy()
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .build();
        final DocRef xsltRef = xsltRefFor(EDITED_ELEMENT_ID);
        final String originalXslt = xsltStore.readDocument(xsltRef).getData();
        SteppingResult latest = null;
        try {
            final SteppingResult opened = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, 0L, 0L))
                    .build());
            latest = opened;
            final StepLocation found = opened.getFoundLocation();
            assertThat(outputOf(opened)).as("output before the save").doesNotContain(MARKER);

            saveXslt(xsltRef, EDITED_XSLT);

            final SteppingResult refreshed = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(found)
                    .sessionUuid(opened.getSessionUuid())
                    .build());
            latest = refreshed;
            final SteppingResult forward = stepUntilComplete(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(found)
                    .sessionUuid(refreshed.getSessionUuid())
                    .build());
            latest = forward;

            final SoftAssertions softly = new SoftAssertions();
            softly.assertThat(outputOf(forward))
                    .as("FORWARD to record 1 after the save shows the saved XSLT's output")
                    .contains(MARKER);
            softly.assertThat(outputOf(refreshed))
                    .as("REFRESH of record 0 after the save shows the saved XSLT's output, as record 1 does")
                    .contains(MARKER);
            softly.assertAll();
        } finally {
            terminate(base, latest);
            saveXslt(xsltRef, originalXslt);
        }
    }

    private void pinSkeletonSweep(final boolean skeletonSweep) {
        setConfigValueMapper(SteppingConfig.class, config -> config.withSkeletonSweep(skeletonSweep));
    }

    // Polls as the stepping screen does: the same request again, under the session id the last answer gave,
    // until the step completes.
    private SteppingResult stepUntilComplete(final PipelineStepRequest request) {
        PipelineStepRequest current = request;
        SteppingResult result = steppingService.step(current);
        final long stopAtMs = System.currentTimeMillis() + STEP_DEADLINE_MS;
        while (!result.isComplete() && System.currentTimeMillis() < stopAtMs) {
            current = current.copy().sessionUuid(result.getSessionUuid()).build();
            result = steppingService.step(current);
        }
        assertThat(result.isComplete()).as("step completed").isTrue();
        return result;
    }

    private long firstMetaId(final PipelineStepRequest base) {
        final SteppingResult probe = stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
        terminate(base, probe);
        assertThat(probe.isFoundRecord()).as("the selection has a record").isTrue();
        return probe.getFoundLocation().getMetaId();
    }

    private void terminate(final PipelineStepRequest base, final SteppingResult result) {
        if (result != null && result.getSessionUuid() != null) {
            steppingService.terminateStepping(base.copy().sessionUuid(result.getSessionUuid()).build());
        }
    }

    private String outputOf(final SteppingResult result) {
        final SharedElementData elementData = result.getStepData().getElementData(EDITED_ELEMENT_ID);
        assertThat(elementData).as(EDITED_ELEMENT_ID + " data").isNotNull();
        return elementData.getOutput();
    }

    private void saveXslt(final DocRef xsltRef, final String data) {
        final XsltDoc current = xsltStore.readDocument(xsltRef);
        xsltStore.writeDocument(current.copy().data(data).build());
    }

    private DocRef xsltRefFor(final String elementId) {
        final PipelineData merged = pipelineDataHolderFactory.create(pipelineDoc()).getMergedPipelineData();
        DocRef xsltRef = null;
        for (final PipelineProperty property : merged.getAddedProperties()) {
            if (elementId.equals(property.getElement())
                && "xslt".equals(property.getName())
                && property.getValue() != null) {
                xsltRef = property.getValue().getEntity();
            }
        }
        assertThat(xsltRef).as(elementId + " references an XSLT").isNotNull();
        return xsltRef;
    }

    private PipelineDoc pipelineDoc() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        return pipelineStore.readDocument(pipelineRef);
    }

    private PipelineStepRequest baseRequest() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .build();
        return PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc())
                .criteria(new FindMetaCriteria(expression))
                .timeout(CLIENT_POLL_TIMEOUT_MS)
                .build();
    }
}
