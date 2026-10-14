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

import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

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
    void testFailureToLogDoesNotStopTheAgent() {
        Mockito.doThrow(new RuntimeException("down")).when(eventLoggingService)
                .log(Mockito.anyString(), Mockito.anyString(), Mockito.any(ProcessEventAction.class));

        auditLog.toolCall(7, "t", "{}", "Run", true, false);
    }
}
