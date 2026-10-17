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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import event.logging.Data;
import event.logging.Outcome;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import event.logging.ProcessType;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

/// Records what the 'Ask Stroom AI' agent does for a user in the audit log: every tool call (with its arguments,
/// shortened, and whether it was run, refused or failed) and every answer. Events from a run while the user was
/// away say so.
public class AiAuditLog {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiAuditLog.class);

    static final String TOOL_CALL_TYPE_ID = "AskStroomAi.toolCall";
    static final String ANSWER_TYPE_ID = "AskStroomAi.answer";
    static final String USER_ACTION_TYPE_ID = "AskStroomAi.userAction";
    static final int MAX_TEXT = 1_000;

    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    AiAuditLog(final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    /// Records a tool call.
    ///
    /// @param chatId     The chat.
    /// @param tool       The tool, as the model named it.
    /// @param arguments  Its arguments.
    /// @param outcome    What happened, e.g. "Run", "Denied by the user".
    /// @param success    False if it failed or was not run.
    /// @param unattended True if the user was away.
    public void toolCall(final int chatId,
                         final String tool,
                         final String arguments,
                         final String outcome,
                         final boolean success,
                         final boolean unattended) {
        log(TOOL_CALL_TYPE_ID, "Ask Stroom AI called tool " + tool + " in chat " + chatId,
                ProcessEventAction.builder()
                        .withType(ProcessType.APPLICATION)
                        .withAction(ProcessAction.CALL)
                        .withCommand(tool)
                        .withArguments(shorten(arguments))
                        .withOutcome(Outcome.builder()
                                .withSuccess(success)
                                .withDescription(outcome)
                                .build())
                        .addData(data("ChatId", String.valueOf(chatId)))
                        .addData(data("Unattended", String.valueOf(unattended)))
                        .build());
    }

    /// Records an answer given by the agent.
    ///
    /// @param chatId     The chat.
    /// @param question   The question.
    /// @param answer     The answer.
    /// @param unattended True if the user was away when it finished.
    public void answer(final int chatId, final String question, final String answer, final boolean unattended) {
        log(ANSWER_TYPE_ID, "Ask Stroom AI answered a question in chat " + chatId,
                ProcessEventAction.builder()
                        .withType(ProcessType.APPLICATION)
                        .withAction(ProcessAction.EXECUTE)
                        .withCommand("Ask Stroom AI")
                        .withArguments(shorten(question))
                        .withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .withDescription(shorten(answer))
                                .build())
                        .addData(data("ChatId", String.valueOf(chatId)))
                        .addData(data("Unattended", String.valueOf(unattended)))
                        .build());
    }

    /// Records the user clicking a button that an MCP tool's result suggested, e.g. to enable a processor filter.
    ///
    /// @param chatId  The chat.
    /// @param action  The kind of action, e.g. enable_processor_filter.
    /// @param label   What the button said, as Stroom wrote it.
    /// @param outcome What happened.
    /// @param success False if it was not done.
    public void userAction(final int chatId,
                           final String action,
                           final String label,
                           final String outcome,
                           final boolean success) {
        log(USER_ACTION_TYPE_ID, "User did a step suggested in Ask Stroom AI chat " + chatId + ": " + label,
                ProcessEventAction.builder()
                        .withType(ProcessType.APPLICATION)
                        .withAction(ProcessAction.EXECUTE)
                        .withCommand(action)
                        .withArguments(shorten(label))
                        .withOutcome(Outcome.builder()
                                .withSuccess(success)
                                .withDescription(shorten(outcome))
                                .build())
                        .addData(data("ChatId", String.valueOf(chatId)))
                        .addData(data("Source", "A suggestion in the chat, from an MCP tool"))
                        .build());
    }

    private void log(final String typeId, final String description, final ProcessEventAction action) {
        try {
            eventLoggingServiceProvider.get().log(typeId, description, action);
        } catch (final RuntimeException e) {
            // Auditing must not stop the agent working, but must not be lost quietly either.
            LOGGER.error(() -> "Unable to log '" + description + "': " + e.getMessage(), e);
        }
    }

    private static Data data(final String name, final String value) {
        return Data.builder().withName(name).withValue(value).build();
    }

    static String shorten(final String text) {
        final String value = NullSafe.string(text);
        return value.length() > MAX_TEXT
                ? value.substring(0, MAX_TEXT) + "… (" + value.length() + " characters)"
                : value;
    }
}
