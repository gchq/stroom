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

package stroom.pipeline.xsltfunctions;

import stroom.ai.api.AiService;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestAskAi extends AbstractXsltFunctionTest<AskAi> {

    private static final String MODEL_NAME = "My Model";
    private static final DocRef MODEL_REF = DocRef.builder()
            .type(OpenAIModelDoc.TYPE)
            .uuid("6e0aa5d3-3a5f-4a8b-9d9e-2a2d5e0f1a11")
            .name(MODEL_NAME)
            .build();

    @Mock
    private AiService mockAiService;

    @InjectMocks
    private AskAi askAi;

    @Test
    void call() {
        Mockito.when(mockAiService.findModelByNameOrUuid(MODEL_NAME))
                .thenReturn(Optional.of(MODEL_REF));
        Mockito.when(mockAiService.chat(MODEL_REF, null, "What is this?"))
                .thenReturn("It is a log event.");

        final Sequence sequence = callFunctionWithSimpleArgs(MODEL_NAME, "What is this?");

        assertThat(getAsStringValue(sequence))
                .hasValue("It is a log event.");

        verifyNoLogCalls();
    }

    @Test
    void call_withSystemPrompt() {
        Mockito.when(mockAiService.findModelByNameOrUuid(MODEL_NAME))
                .thenReturn(Optional.of(MODEL_REF));
        Mockito.when(mockAiService.chat(MODEL_REF, "You are a log parser", "What is this?"))
                .thenReturn("It is a log event.");

        final Sequence sequence = callFunctionWithSimpleArgs(
                MODEL_NAME, "What is this?", "You are a log parser");

        assertThat(getAsStringValue(sequence))
                .hasValue("It is a log event.");

        verifyNoLogCalls();
    }

    /**
     * The model is looked up once however many times the function is called, as a pipeline will call this
     * for every record.
     */
    @Test
    void call_modelLookupIsCached() {
        Mockito.when(mockAiService.findModelByNameOrUuid(MODEL_NAME))
                .thenReturn(Optional.of(MODEL_REF));
        Mockito.when(mockAiService.chat(Mockito.eq(MODEL_REF), Mockito.isNull(), Mockito.anyString()))
                .thenReturn("It is a log event.");

        callFunctionWithSimpleArgs(MODEL_NAME, "What is this?");
        callFunctionWithSimpleArgs(MODEL_NAME, "What is that?");

        Mockito.verify(mockAiService, Mockito.times(1))
                .findModelByNameOrUuid(MODEL_NAME);

        verifyNoLogCalls();
    }

    @Test
    void call_noModel() {
        final Sequence sequence = callFunctionWithSimpleArgs("", "What is this?");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.WARNING, "no model specified");
    }

    @Test
    void call_unknownModel() {
        Mockito.when(mockAiService.findModelByNameOrUuid(MODEL_NAME))
                .thenReturn(Optional.empty());

        final Sequence sequence = callFunctionWithSimpleArgs(MODEL_NAME, "What is this?");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.WARNING, "ai model not found");
    }

    /**
     * A failed lookup is not remembered, so a transient failure does not silently stop every later record
     * producing output, and it is reported once as an error rather than also as a misleading "not found".
     */
    @Test
    void call_modelLookupErrorIsNotCached() {
        Mockito.when(mockAiService.findModelByNameOrUuid(MODEL_NAME))
                .thenThrow(new RuntimeException("Lookup went bang"));

        callFunctionWithSimpleArgs(MODEL_NAME, "What is this?");
        callFunctionWithSimpleArgs(MODEL_NAME, "What is that?");

        Mockito.verify(mockAiService, Mockito.times(2))
                .findModelByNameOrUuid(MODEL_NAME);

        final List<LogArgs> logArgsList = verifyLogCalls(2);
        assertLogCall(logArgsList.get(0), Severity.ERROR, "lookup went bang");
        assertLogCall(logArgsList.get(1), Severity.ERROR, "lookup went bang");
    }

    @Test
    void call_error() {
        Mockito.when(mockAiService.findModelByNameOrUuid(MODEL_NAME))
                .thenReturn(Optional.of(MODEL_REF));
        Mockito.when(mockAiService.chat(MODEL_REF, null, "What is this?"))
                .thenThrow(new RuntimeException("Bad happened"));

        final Sequence sequence = callFunctionWithSimpleArgs(MODEL_NAME, "What is this?");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.ERROR, "bad happened");
    }

    @Override
    AskAi getXsltFunction() {
        return askAi;
    }

    @Override
    String getFunctionName() {
        return AskAi.FUNCTION_NAME;
    }
}
