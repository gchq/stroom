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

package stroom.ai.impl;

import stroom.event.logging.api.StroomEventLoggingService;

import event.logging.Device;
import event.logging.Event;
import event.logging.EventDetail;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import event.logging.ProcessType;
import event.logging.SystemDetail;
import event.logging.User;
import event.logging.impl.DefaultEventSerializer;
import event.logging.impl.DefaultXMLValidator;
import event.logging.impl.ExceptionAndLoggingErrorHandler;
import event.logging.impl.SchemaLocator;
import event.logging.impl.ValidationExceptionBehaviourMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TestAiAuditLog {

    private final StroomEventLoggingService eventLoggingService = Mockito.mock(StroomEventLoggingService.class);
    private final AiAuditLog auditLog = new AiAuditLog(() -> eventLoggingService);

    @Test
    void testToolCall() {
        auditLog.toolCall(7, "stroom__create_feed", "{\"name\":\"X\"}", "Denied by the user", false, true);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.TOOL_CALL_TYPE_ID), Mockito.contains("chat 7"),
                action.capture());
        assertThat(action.getValue().getAction()).isEqualTo(ProcessAction.CALL);
        assertThat(action.getValue().getType()).isEqualTo(ProcessType.APPLICATION);
        assertThat(action.getValue().getCommand()).isEqualTo("stroom__create_feed");
        assertThat(action.getValue().getArguments()).isEqualTo("{\"name\":\"X\"}");
        assertThat(action.getValue().getOutcome().isSuccess()).isFalse();
        assertThat(action.getValue().getOutcome().getDescription()).isEqualTo("Denied by the user");
        // Events from a run while the user was away say so.
        assertThat(action.getValue().getData())
                .anySatisfy(data -> {
                    assertThat(data.getName()).isEqualTo("Unattended");
                    assertThat(data.getValue()).isEqualTo("true");
                });
    }

    @Test
    void testAnswerIsShortened() {
        auditLog.answer(7, "q", "a".repeat(5_000), false);

        final ArgumentCaptor<ProcessEventAction> action = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService).log(Mockito.eq(AiAuditLog.ANSWER_TYPE_ID), Mockito.anyString(),
                action.capture());
        assertThat(action.getValue().getOutcome().getDescription())
                .startsWith("a".repeat(AiAuditLog.MAX_TEXT))
                .endsWith("(5000 characters)");
    }

    @Test
    void testEventsAreValid() {
        // The schema needs the process's type, which the audit log once left out, so every event was invalid.
        auditLog.toolCall(7, "stroom__create_pipeline", "{\"name\":\"X\"}", "Run", true, false);
        auditLog.answer(7, "q", "a", false);
        auditLog.userAction(7, "enable_processor_filter", "Enable", "Enabled", true);

        final ArgumentCaptor<ProcessEventAction> actions = ArgumentCaptor.forClass(ProcessEventAction.class);
        Mockito.verify(eventLoggingService, Mockito.times(3)).log(Mockito.anyString(), Mockito.anyString(),
                actions.capture());
        final DefaultXMLValidator validator = new DefaultXMLValidator(SchemaLocator.getSchemaLocation(),
                new ExceptionAndLoggingErrorHandler(), ValidationExceptionBehaviourMode.THROW);
        assertThat(actions.getAllValues())
                .hasSize(3)
                .allSatisfy(action -> assertThatCode(() -> validator.validate(
                        new DefaultEventSerializer().serialize(event(action))))
                        .doesNotThrowAnyException());
    }

    @Test
    void testFailureToLogDoesNotStopTheAgent() {
        Mockito.doThrow(new RuntimeException("down")).when(eventLoggingService)
                .log(Mockito.anyString(), Mockito.anyString(), Mockito.any(ProcessEventAction.class));

        auditLog.toolCall(7, "t", "{}", "Run", true, false);
    }

    // An event as Stroom logs it, with the least the schema needs around the action.
    private static Event event(final ProcessEventAction action) {
        return Event.builder()
                .withEventTime(EventTime.builder()
                        .withTimeCreated(new Date())
                        .build())
                .withEventSource(EventSource.builder()
                        .withSystem(SystemDetail.builder()
                                .withName("Stroom")
                                .withEnvironment("Test")
                                .build())
                        .withGenerator("Test")
                        .withDevice(Device.builder()
                                .withHostName("localhost")
                                .build())
                        .withUser(User.builder()
                                .withId("admin")
                                .build())
                        .build())
                .withEventDetail(EventDetail.builder()
                        .withTypeId(AiAuditLog.TOOL_CALL_TYPE_ID)
                        .withDescription("Test")
                        .withEventAction(action)
                        .build())
                .build();
    }
}
