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
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// Reproduction for the report that changing an XSLT imported by the pipeline's XSLT isn't picked up when
/// stepping. The pipeline's XSLT imports another (by name or by uuid); the imported XSLT is changed and saved
/// part way through a stepping session; refreshing the record shown, or stepping on, must show the change.
class TestSteppingServiceImportedXslt extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String XSLT_ELEMENT_ID = "translationFilter";
    private static final String BEFORE_MARKER = "ImportedBefore";
    private static final String AFTER_MARKER = "ImportedAfter";
    private static final String NOT_FOUND_MESSAGE = "No XSLT found with name or UUID";
    private static final long CLIENT_POLL_TIMEOUT_MS = 40L;
    private static final long STEP_DEADLINE_MS = 60_000L;

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

    @ParameterizedTest(name = "skeletonSweep={0}, importBy={1}")
    @CsvSource({"true, name", "false, name", "true, uuid", "false, uuid"})
    void savedChangeToImportedXsltIsShown(final boolean skeletonSweep, final String importBy) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        final DocRef pipelineXsltRef = xsltRefFor(XSLT_ELEMENT_ID);
        final String originalPipelineXslt = xsltStore.readDocument(pipelineXsltRef).getData();
        final DocRef importedRef = xsltStore.createDocument("imported-" + UUID.randomUUID());
        SteppingResult latest = null;
        try {
            saveXslt(importedRef, outputting(BEFORE_MARKER));
            final String href = "uuid".equals(importBy)
                    ? importedRef.getUuid()
                    : importedRef.getName();
            saveXslt(pipelineXsltRef, importing(href));

            final SteppingResult opened = stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
            latest = opened;
            assertThat(opened.isFoundRecord()).as("opening step found a record").isTrue();
            final StepLocation found = opened.getFoundLocation();
            assertThat(outputOf(opened)).as("output before the change").contains(BEFORE_MARKER);

            saveXslt(importedRef, outputting(AFTER_MARKER));

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
            terminate(base, latest);
            latest = null;

            // A new stepping session, as when the user closes and reopens the stepper.
            final SteppingResult reopened = stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
            latest = reopened;

            final SoftAssertions softly = new SoftAssertions();
            softly.assertThat(outputOf(refreshed))
                    .as("REFRESH of the same record after saving the imported XSLT")
                    .contains(AFTER_MARKER);
            softly.assertThat(outputOf(forward))
                    .as("FORWARD after saving the imported XSLT")
                    .contains(AFTER_MARKER);
            softly.assertThat(outputOf(reopened))
                    .as("first record of a new stepping session after saving the imported XSLT")
                    .contains(AFTER_MARKER);
            softly.assertAll();
        } finally {
            terminate(base, latest);
            saveXslt(pipelineXsltRef, originalPipelineXslt);
        }
    }

    /// The pipeline's XSLT imports an XSLT that doesn't exist yet (or whose name doesn't match yet), so stepping
    /// reports that no XSLT has that name. The XSLT is then created with that name. Refreshing the record, stepping
    /// on, or opening a new stepping session must resolve the import.
    ///
    /// `unsaved` is true when the import is typed in the stepping editor (sent as injected code) rather than
    /// saved.
    @ParameterizedTest(name = "skeletonSweep={0}, unsaved={1}")
    @CsvSource({"true, false", "false, false", "true, true", "false, true"})
    void importResolvesOnceTheImportedXsltIsCreated(final boolean skeletonSweep, final boolean unsaved) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        final DocRef pipelineXsltRef = xsltRefFor(XSLT_ELEMENT_ID);
        final String originalPipelineXslt = xsltStore.readDocument(pipelineXsltRef).getData();
        final String importedName = "later-" + UUID.randomUUID();
        final String importingXslt = importing(importedName);
        final Map<String, String> code = unsaved
                ? Map.of(XSLT_ELEMENT_ID, importingXslt)
                : Map.of();
        SteppingResult latest = null;
        try {
            if (!unsaved) {
                saveXslt(pipelineXsltRef, importingXslt);
            }

            final SteppingResult opened = stepUntilComplete(base.copy()
                    .stepType(StepType.FIRST)
                    .code(code)
                    .build());
            latest = opened;
            final String errorsBefore = xsltErrors(opened);
            assertThat(errorsBefore).as("errors before the imported XSLT exists").contains(NOT_FOUND_MESSAGE);

            final DocRef importedRef = xsltStore.createDocument(importedName);
            saveXslt(importedRef, outputting(AFTER_MARKER));

            final StepLocation location = opened.isFoundRecord()
                    ? opened.getFoundLocation()
                    : null;
            final SteppingResult refreshed = location == null
                    ? null
                    : stepUntilComplete(base.copy()
                            .stepType(StepType.REFRESH)
                            .stepLocation(location)
                            .sessionUuid(opened.getSessionUuid())
                            .code(code)
                            .build());
            if (refreshed != null) {
                latest = refreshed;
            }
            final SteppingResult first = stepUntilComplete(base.copy()
                    .stepType(StepType.FIRST)
                    .sessionUuid(latest.getSessionUuid())
                    .code(code)
                    .build());
            latest = first;
            terminate(base, latest);
            latest = null;

            final SteppingResult reopened = stepUntilComplete(base.copy()
                    .stepType(StepType.FIRST)
                    .code(code)
                    .build());
            latest = reopened;

            final SoftAssertions softly = new SoftAssertions();
            softly.assertThat(opened.isFoundRecord())
                    .as("opening step found a record (so REFRESH could be tried)")
                    .isTrue();
            if (refreshed != null) {
                softly.assertThat(xsltErrors(refreshed) + outputOrEmpty(refreshed))
                        .as("REFRESH after creating the imported XSLT")
                        .doesNotContain(NOT_FOUND_MESSAGE)
                        .contains(AFTER_MARKER);
            }
            softly.assertThat(xsltErrors(first) + outputOrEmpty(first))
                    .as("FIRST in the same session after creating the imported XSLT")
                    .doesNotContain(NOT_FOUND_MESSAGE)
                    .contains(AFTER_MARKER);
            softly.assertThat(xsltErrors(reopened) + outputOrEmpty(reopened))
                    .as("new stepping session after creating the imported XSLT")
                    .doesNotContain(NOT_FOUND_MESSAGE)
                    .contains(AFTER_MARKER);
            softly.assertAll();
        } finally {
            terminate(base, latest);
            saveXslt(pipelineXsltRef, originalPipelineXslt);
        }
    }

    // The XSLT element's errors, plus any general errors, as text.
    private static String xsltErrors(final SteppingResult result) {
        final StringBuilder sb = new StringBuilder();
        NullSafe.forEach(result.getGeneralErrors(), error -> sb.append(error).append('\n'));
        final Indicators indicators = NullSafe.get(
                result.getStepData(),
                stepData -> stepData.getElementData(XSLT_ELEMENT_ID),
                SharedElementData::getIndicators);
        if (indicators != null) {
            indicators.getErrorList().forEach(error -> sb.append(error).append('\n'));
        }
        return sb.toString();
    }

    private static String outputOrEmpty(final SteppingResult result) {
        final String output = NullSafe.get(
                result.getStepData(),
                stepData -> stepData.getElementData(XSLT_ELEMENT_ID),
                SharedElementData::getOutput);
        return NullSafe.string(output);
    }

    private static String outputting(final String elementName) {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <%s/>
                  </xsl:template>
                </xsl:stylesheet>
                """.formatted(elementName);
    }

    private static String importing(final String href) {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:import href="%s"/>
                </xsl:stylesheet>
                """.formatted(href);
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

    private void terminate(final PipelineStepRequest base, final SteppingResult result) {
        if (result != null && result.getSessionUuid() != null) {
            steppingService.terminateStepping(base.copy().sessionUuid(result.getSessionUuid()).build());
        }
    }

    private String outputOf(final SteppingResult result) {
        assertThat(result.getStepData()).as("step data").isNotNull();
        final SharedElementData elementData = result.getStepData().getElementData(XSLT_ELEMENT_ID);
        assertThat(elementData).as(XSLT_ELEMENT_ID + " data").isNotNull();
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
