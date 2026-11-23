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

import stroom.analytics.api.EmailMessage;
import stroom.analytics.api.EmailService;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.state.PipelineContext;
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSendEmail extends AbstractXsltFunctionTest<SendEmail> {

    @Mock
    private EmailService mockEmailService;

    private PipelineContext pipelineContext;
    private XsltConfig xsltConfig;
    private SendEmail sendEmail;

    @BeforeEach
    void setUp() {
        pipelineContext = new PipelineContext();
        xsltConfig = new XsltConfig();
        sendEmail = new SendEmail(mockEmailService, pipelineContext, () -> xsltConfig);
    }

    @Test
    void sendsEmailToRecipients() {
        final Sequence sequence = callFunctionWithSimpleArgs(
                "a@example.com;b@example.com", "Alert", "Something happened");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);
        Mockito.verify(mockEmailService).sendEmail(new EmailMessage(
                "a@example.com;b@example.com", null, null, "Alert", "Something happened"));
        verifyNoLogCalls();
    }

    @Test
    void sendsEmailWithCcAndBcc() {
        callFunctionWithSimpleArgs(
                "a@example.com", "Alert", "Something happened", "c@example.com", "d@example.com");

        Mockito.verify(mockEmailService).sendEmail(new EmailMessage(
                "a@example.com", "c@example.com", "d@example.com", "Alert", "Something happened"));
        verifyNoLogCalls();
    }

    @Test
    void sendsEmailWithCcOnly() {
        callFunctionWithSimpleArgs("a@example.com", "Alert", "Something happened", "c@example.com");

        Mockito.verify(mockEmailService).sendEmail(new EmailMessage(
                "a@example.com", "c@example.com", null, "Alert", "Something happened"));
        verifyNoLogCalls();
    }

    /// Stepping repeatedly runs the XSLT over the same data, so it must never email anyone.
    @Test
    void doesNotSendWhileStepping() {
        pipelineContext.setStepping(true);

        final Sequence sequence = callFunctionWithSimpleArgs("a@example.com", "Alert", "Something happened");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);
        Mockito.verifyNoInteractions(mockEmailService);
        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.INFO, "not sent while stepping", "a@example.com", "Alert");
    }

    @Test
    void invalidEmailIsReportedAsAnError() {
        Mockito.doThrow(new IllegalArgumentException("There must be at least one recipient"))
                .when(mockEmailService)
                .sendEmail(Mockito.any());

        final Sequence sequence = callFunctionWithSimpleArgs("", "Alert", "Something happened");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);
        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.ERROR, "at least one recipient");
    }

    @Test
    void sendFailureIsReportedAsAnError() {
        Mockito.doThrow(new RuntimeException("Connection refused"))
                .when(mockEmailService)
                .sendEmail(Mockito.any());

        callFunctionWithSimpleArgs("a@example.com", "Alert", "Something happened");

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.ERROR, "connection refused");
    }

    @Test
    void sendsToAllowedRecipients() {
        setLimits(List.of("a@example.com", "@other.com"), 10);

        callFunctionWithSimpleArgs("A@Example.com", "Alert", "Body", "x@other.com; y@OTHER.com", "");

        Mockito.verify(mockEmailService).sendEmail(Mockito.any());
        verifyNoLogCalls();
    }

    @Test
    void refusesRecipientNotAllowed() {
        setLimits(List.of("a@example.com", "@other.com"), 10);

        callFunctionWithSimpleArgs("a@example.com", "Alert", "Body", "", "b@sub.other.com");

        Mockito.verifyNoInteractions(mockEmailService);
        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.ERROR, "b@sub.other.com", "not an allowed recipient");
    }

    @Test
    void refusesAddressThatIsNotPlain() {
        setLimits(List.of("a@example.com"), 10);

        for (final String to : List.of(
                "Someone <b@elsewhere.com>",
                "a@example.com, b@elsewhere.com",
                "\"a@example.com\"@elsewhere.com")) {
            assertThat(SendEmail.findRefusedRecipient(List.of("a@example.com"), to))
                    .as(to)
                    .isNotNull();
        }
        callFunctionWithSimpleArgs("Someone <a@example.com>", "Alert", "Body");
        Mockito.verifyNoInteractions(mockEmailService);
    }

    @Test
    void anyRecipientWhenNoneListed() {
        assertThat(SendEmail.findRefusedRecipient(List.of(), "Someone <b@elsewhere.com>")).isNull();
    }

    @Test
    void stopsAtLimitForRun() {
        setLimits(List.of(), 2);

        for (int i = 0; i < 3; i++) {
            callFunctionWithSimpleArgs("a@example.com", "Alert", "Body");
        }

        Mockito.verify(mockEmailService, Mockito.times(2)).sendEmail(Mockito.any());
        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.ERROR, "limit of 2");
    }

    @Test
    void sendsNothingWithZeroLimit() {
        setLimits(List.of(), 0);

        callFunctionWithSimpleArgs("a@example.com", "Alert", "Body");

        Mockito.verifyNoInteractions(mockEmailService);
        assertLogCall(verifySingleLogCall(), Severity.ERROR, "limit of 0");
    }

    @Test
    void steppingStillChecksRecipients() {
        // So that stepping shows an address would be refused.
        setLimits(List.of("a@example.com"), 10);
        pipelineContext.setStepping(true);

        callFunctionWithSimpleArgs("b@example.com", "Alert", "Body");

        assertLogCall(verifySingleLogCall(), Severity.ERROR, "not an allowed recipient");
    }

    private void setLimits(final List<String> allowedRecipients, final int maxEmailsPerRun) {
        xsltConfig = new XsltConfig(null, null, null, null, allowedRecipients, maxEmailsPerRun);
    }

    @Override
    SendEmail getXsltFunction() {
        return sendEmail;
    }

    @Override
    String getFunctionName() {
        return SendEmail.FUNCTION_NAME;
    }
}
