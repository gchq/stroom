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
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// Reproduction for the report that stepping a pipeline whose data splitter is invalid gives no feedback, only
/// empty source and output panes at the parser element.
///
/// The data splitter is broken either in the stepping editor (sent as injected code, as an unsaved edit is) or
/// by saving the text converter. Either way, the step result must carry the error somewhere the stepping screen
/// can show it: the parser element's indicators or the result's general errors.
class TestSteppingServiceInvalidDataSplitter extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "TEST_DS_PARSER-EVENTS";
    private static final String PARSER_ELEMENT_ID = "dsParser";
    private static final DocRef TEXT_CONVERTER_REF = new DocRef(
            TextConverterDoc.TYPE, "7e4452ed-bf26-4187-b849-e3e721780cba", "CSV_WITH_HEADER");
    private static final long CLIENT_POLL_TIMEOUT_MS = 40L;
    private static final long STEP_DEADLINE_MS = 60_000L;

    // Not well-formed XML: the root element is never closed.
    private static final String MALFORMED_XML = """
            <?xml version="1.1" encoding="UTF-8"?>
            <dataSplitter xmlns="data-splitter:3" version="3.0">
              <split delimiter="\\n">
                <group value="$1"/>
              </split>
            """;

    // Well-formed, but the regex can't be compiled.
    private static final String INVALID_REGEX = """
            <?xml version="1.1" encoding="UTF-8"?>
            <dataSplitter
                xmlns="data-splitter:3"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="data-splitter:3 file://data-splitter-v3.0.xsd"
                version="3.0">
              <regex pattern="([unclosed">
                <data value="$1"/>
              </regex>
            </dataSplitter>
            """;

    // Well-formed, but an element the schema doesn't allow.
    private static final String UNKNOWN_ELEMENT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <dataSplitter
                xmlns="data-splitter:3"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="data-splitter:3 file://data-splitter-v3.0.xsd"
                version="3.0">
              <notAnElement/>
            </dataSplitter>
            """;

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private TextConverterStore textConverterStore;
    @Inject
    private MetaService metaService;

    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            importConfig();
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

    /// Control: the valid data splitter steps to a record with output at the parser.
    @ParameterizedTest(name = "skeletonSweep={0}")
    @ValueSource(booleans = {true, false})
    void validDataSplitterSteps(final boolean skeletonSweep) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        SteppingResult result = null;
        try {
            result = stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
            assertThat(result.isFoundRecord()).as("found a record").isTrue();
            assertThat(parserOutput(result)).as("parser output").isNotBlank();
        } finally {
            terminate(base, result);
        }
    }

    /// Control: stepping on from the last record of a stream that steps normally finds nothing and returns no
    /// step data, so the stepping screen keeps showing the last record rather than the stream's start-up
    /// indicators.
    @ParameterizedTest(name = "skeletonSweep={0}")
    @ValueSource(booleans = {true, false})
    void steppingPastTheLastRecordReturnsNoStepData(final boolean skeletonSweep) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        SteppingResult result = null;
        try {
            final SteppingResult last = stepUntilComplete(base.copy().stepType(StepType.LAST).build());
            result = last;
            assertThat(last.isFoundRecord()).as("found the last record").isTrue();

            final SteppingResult beyond = stepUntilComplete(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(last.getFoundLocation())
                    .sessionUuid(last.getSessionUuid())
                    .build());
            result = beyond;
            assertThat(beyond.isFoundRecord()).as("found a record beyond the last").isFalse();
            assertThat(beyond.getStepData()).as("step data beyond the last record").isNull();
            assertThat(beyond.getGeneralErrors()).as("general errors beyond the last record").isEmpty();
        } finally {
            terminate(base, result);
        }
    }

    /// The data splitter is broken in the stepping editor, so it is sent as injected code. The stream is
    /// opened either with the First button or as the stepping screen opens a stream (a REFRESH of record 0).
    @ParameterizedTest(name = "skeletonSweep={0}, splitter={1}, openWithRefresh={2}")
    @MethodSource("invalidSplitterCases")
    void unsavedInvalidDataSplitterIsReported(final boolean skeletonSweep,
                                              final String splitter,
                                              final boolean openWithRefresh) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        SteppingResult result = null;
        try {
            result = stepUntilComplete(openingRequest(base, openWithRefresh)
                    .code(Map.of(PARSER_ELEMENT_ID, splitter(splitter)))
                    .build());
            assertErrorIsReported(result);
        } finally {
            terminate(base, result);
        }
    }

    /// The broken data splitter is saved, so no code is sent.
    @ParameterizedTest(name = "skeletonSweep={0}, splitter={1}, openWithRefresh={2}")
    @MethodSource("invalidSplitterCases")
    void savedInvalidDataSplitterIsReported(final boolean skeletonSweep,
                                            final String splitter,
                                            final boolean openWithRefresh) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        final String original = textConverterStore.readDocument(TEXT_CONVERTER_REF).getData();
        SteppingResult result = null;
        try {
            saveTextConverter(splitter(splitter));
            result = stepUntilComplete(openingRequest(base, openWithRefresh).build());
            assertErrorIsReported(result);
        } finally {
            terminate(base, result);
            saveTextConverter(original);
        }
    }

    /// The stream is stepped with a valid data splitter first, so its records are captured, and the data
    /// splitter is then broken in the stepping editor. Refreshing the record shown, and stepping on to the
    /// next one, must both report the error at the parser.
    @ParameterizedTest(name = "skeletonSweep={0}, splitter={1}")
    @MethodSource("invalidSplitterAfterSteppingCases")
    void invalidDataSplitterAfterSteppingIsReportedOnEveryRecord(final boolean skeletonSweep,
                                                                 final String splitter) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        SteppingResult latest = null;
        try {
            final SteppingResult opened = stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
            latest = opened;
            assertThat(opened.isFoundRecord()).as("opening step with the valid data splitter").isTrue();
            final StepLocation found = opened.getFoundLocation();
            final Map<String, String> code = Map.of(PARSER_ELEMENT_ID, splitter(splitter));

            final SteppingResult refreshed = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(found)
                    .sessionUuid(opened.getSessionUuid())
                    .code(code)
                    .build());
            latest = refreshed;
            final SteppingResult forward = stepUntilComplete(base.copy()
                    .stepType(StepType.FORWARD)
                    .stepLocation(found)
                    .sessionUuid(refreshed.getSessionUuid())
                    .code(code)
                    .build());
            latest = forward;

            final SoftAssertions softly = new SoftAssertions();
            softly.check(() -> assertErrorIsReported(refreshed, "REFRESH of the record shown"));
            softly.check(() -> assertErrorIsReported(forward, "FORWARD to the next record"));
            softly.assertAll();
        } finally {
            terminate(base, latest);
        }
    }

    /// The data splitter is broken in the stepping editor and the record refreshed, which reports the error;
    /// the edit is then reverted and the record refreshed again. The error must go, and the record's output
    /// come back. `sendRevertedCode` is true when the reverted text is still sent as editor code (the editor
    /// stays dirty), false when the editor is clean again and no code is sent.
    @ParameterizedTest(name = "skeletonSweep={0}, splitter={1}, sendRevertedCode={2}")
    @MethodSource("revertedSplitterCases")
    void errorClearsWhenTheDataSplitterIsReverted(final boolean skeletonSweep,
                                                  final String splitter,
                                                  final boolean sendRevertedCode) {
        pinSkeletonSweep(skeletonSweep);
        final PipelineStepRequest base = baseRequest();
        final String valid = textConverterStore.readDocument(TEXT_CONVERTER_REF).getData();
        SteppingResult latest = null;
        try {
            final SteppingResult opened = stepUntilComplete(base.copy().stepType(StepType.FIRST).build());
            latest = opened;
            assertThat(opened.isFoundRecord()).as("opening step with the valid data splitter").isTrue();
            final StepLocation found = opened.getFoundLocation();

            final SteppingResult broken = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(found)
                    .sessionUuid(opened.getSessionUuid())
                    .code(Map.of(PARSER_ELEMENT_ID, splitter(splitter)))
                    .build());
            latest = broken;
            assertErrorIsReported(broken, "REFRESH with the broken data splitter");

            final SteppingResult reverted = stepUntilComplete(base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(found)
                    .sessionUuid(broken.getSessionUuid())
                    .code(sendRevertedCode
                            ? Map.of(PARSER_ELEMENT_ID, valid)
                            : Map.of())
                    .build());
            latest = reverted;

            final Indicators parserIndicators = NullSafe.get(
                    reverted.getStepData(),
                    stepData -> stepData.getElementData(PARSER_ELEMENT_ID),
                    SharedElementData::getIndicators);
            final SoftAssertions softly = new SoftAssertions();
            softly.assertThat(reverted.isFoundRecord()).as("REFRESH after the revert found the record").isTrue();
            softly.assertThat(NullSafe.get(parserIndicators, Indicators::getMaxSeverity))
                    .as("parser errors after the revert: %s", parserIndicators)
                    .isNull();
            if (reverted.isFoundRecord()) {
                softly.assertThat(parserOutput(reverted)).as("parser output after the revert").isNotBlank();
            }
            softly.assertAll();
        } finally {
            terminate(base, latest);
        }
    }

    static List<Arguments> revertedSplitterCases() {
        final List<Arguments> cases = new ArrayList<>();
        for (final boolean skeletonSweep : new boolean[]{true, false}) {
            for (final String splitter : List.of("MALFORMED_XML", "UNKNOWN_ELEMENT")) {
                for (final boolean sendRevertedCode : new boolean[]{false, true}) {
                    cases.add(Arguments.of(skeletonSweep, splitter, sendRevertedCode));
                }
            }
        }
        return cases;
    }

    static List<Arguments> invalidSplitterAfterSteppingCases() {
        final List<Arguments> cases = new ArrayList<>();
        for (final boolean skeletonSweep : new boolean[]{true, false}) {
            for (final String splitter : List.of("MALFORMED_XML", "INVALID_REGEX", "UNKNOWN_ELEMENT")) {
                cases.add(Arguments.of(skeletonSweep, splitter));
            }
        }
        return cases;
    }

    static List<Arguments> invalidSplitterCases() {
        final List<Arguments> cases = new ArrayList<>();
        for (final boolean skeletonSweep : new boolean[]{true, false}) {
            for (final String splitter : List.of("MALFORMED_XML", "INVALID_REGEX", "UNKNOWN_ELEMENT")) {
                for (final boolean openWithRefresh : new boolean[]{false, true}) {
                    cases.add(Arguments.of(skeletonSweep, splitter, openWithRefresh));
                }
            }
        }
        return cases;
    }

    // The first step on the stream: the First button, or a REFRESH of its first record.
    private PipelineStepRequest.Builder openingRequest(final PipelineStepRequest base,
                                                       final boolean openWithRefresh) {
        if (openWithRefresh) {
            final long metaId = metaService.find(base.getCriteria()).getFirst().getId();
            return base.copy()
                    .stepType(StepType.REFRESH)
                    .stepLocation(new StepLocation(metaId, 0L, 0L));
        }
        return base.copy().stepType(StepType.FIRST);
    }

    private static String splitter(final String name) {
        return switch (name) {
            case "MALFORMED_XML" -> MALFORMED_XML;
            case "INVALID_REGEX" -> INVALID_REGEX;
            case "UNKNOWN_ELEMENT" -> UNKNOWN_ELEMENT;
            default -> throw new IllegalArgumentException(name);
        };
    }

    // The stepping screen shows an element's errors in its log pane and on the pipeline tree, so the parser
    // element must carry the data splitter's error.
    private void assertErrorIsReported(final SteppingResult result) {
        assertErrorIsReported(result, "the step");
    }

    private void assertErrorIsReported(final SteppingResult result, final String step) {
        final Indicators parserIndicators = NullSafe.get(
                result.getStepData(),
                stepData -> stepData.getElementData(PARSER_ELEMENT_ID),
                SharedElementData::getIndicators);
        final Severity maxSeverity = NullSafe.get(parserIndicators, Indicators::getMaxSeverity);

        assertThat(maxSeverity != null && maxSeverity.greaterThanOrEqual(Severity.ERROR))
                .as("""
                        %s: the parser element reports the invalid data splitter as an error
                          foundRecord:      %s
                          foundLocation:    %s
                          generalErrors:    %s
                          stepData is null: %s
                          parser data:      %s""",
                        step,
                        result.isFoundRecord(),
                        result.getFoundLocation(),
                        result.getGeneralErrors(),
                        result.getStepData() == null,
                        NullSafe.get(result.getStepData(), stepData -> stepData.getElementData(PARSER_ELEMENT_ID)))
                .isTrue();
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

    private String parserOutput(final SteppingResult result) {
        final SharedElementData elementData = result.getStepData().getElementData(PARSER_ELEMENT_ID);
        assertThat(elementData).as(PARSER_ELEMENT_ID + " data").isNotNull();
        return elementData.getOutput();
    }

    private void saveTextConverter(final String data) {
        final TextConverterDoc current = textConverterStore.readDocument(TEXT_CONVERTER_REF);
        textConverterStore.writeDocument(current.copy().data(data).build());
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
