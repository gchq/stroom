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

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiUserActionResult;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// A chat in "Ask Stroom AI" run as a user, for the evaluation's skill cases: the user's questions asked in turn on
/// a chat model given by its base URL (a scripted [FakeModelServer], or one whose agent turns a real model answers),
/// every form the run stops for answered as the case's user would, every call it asks approval for approved, and the
/// chat read back:
/// its tool calls with their results and the buttons they offered, and the model's replies.
///
/// Skills live in the chat only (`propose_skill` offers a 'Review and apply' button there, and `undo` reads the chat's
/// earlier proposals), so a skill case runs here rather than over `/mcp`.
public final class EvalChat {

    private static final Duration POLL = Duration.ofMillis(200);

    private final AskStroomAIService askStroomAIService;
    private final AiService aiService;
    private final OpenAIModelStore modelStore;
    private final UserActionService userActionService;
    private final AiChatRunManager runManager;
    private final SecurityContext securityContext;
    private final DocumentPermissionService documentPermissionService;

    @Inject
    EvalChat(final AskStroomAIService askStroomAIService,
             final AiService aiService,
             final OpenAIModelStore modelStore,
             final UserActionService userActionService,
             final AiChatRunManager runManager,
             final SecurityContext securityContext,
             final DocumentPermissionService documentPermissionService) {
        this.askStroomAIService = askStroomAIService;
        this.aiService = aiService;
        this.modelStore = modelStore;
        this.userActionService = userActionService;
        this.runManager = runManager;
        this.securityContext = securityContext;
        this.documentPermissionService = documentPermissionService;
    }

    /// A model doc for a chat model at a base URL, with tools on, made by an administrator and usable by the user.
    ///
    /// @param admin   The administrator who makes it.
    /// @param user    The user who chats with it.
    /// @param name    Its name.
    /// @param baseUrl The model's OpenAI-compatible base URL, e.g. [FakeModelServer#baseUrl].
    /// @return The model doc.
    public DocRef model(final UserRef admin, final UserRef user, final String name, final String baseUrl) {
        return securityContext.asUserResult(admin, () -> {
            final DocRef ref = modelStore.createDocument(name);
            modelStore.writeDocument(modelStore.readDocument(ref).copy()
                    .baseUrl(baseUrl)
                    .apiKey("key")
                    .modelId("eval")
                    .toolsEnabled(true)
                    .build());
            if (!admin.equals(user)) {
                documentPermissionService.setPermission(ref, user, DocumentPermission.USE);
            }
            return ref;
        });
    }

    /// A new chat of the user's.
    ///
    /// @param user The user.
    /// @return The chat.
    public AiChat create(final UserRef user) {
        return securityContext.asUserResult(user, askStroomAIService::createChat);
    }

    /// Asks a question as the user and waits for the answer, answering each form the run stops for.
    ///
    /// @param user     The user.
    /// @param chat     The chat.
    /// @param model    The model doc to answer with ([#model]).
    /// @param question What the user types.
    /// @param forms    Each form's answer (its fields' values), or null to decline it.
    /// @param timeout  How long the answer may take.
    /// @return The messages the turn added, from the question on.
    public List<AiChatMessage> ask(final UserRef user,
                                   final AiChat chat,
                                   final DocRef model,
                                   final String question,
                                   final Function<Form, Map<String, String>> forms,
                                   final Duration timeout) {
        final int before = messages(user, chat.getId()).size();
        final long answersBefore = answers(messages(user, chat.getId()));
        securityContext.asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(model).build(), null, question, AiAgentMode.AGENT)));
        final Set<Integer> handled = new HashSet<>();
        final Instant until = Instant.now().plus(timeout);
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = securityContext.asUserResult(user, () -> {
                askStroomAIService.pollMessages(chat.getId(), new AiChatPollRequest(0));
                return aiService.getMessages(chat.getId());
            });
            for (final AiChatMessage message : messages) {
                if (message.getMessageType() == AiMessageType.APPROVAL_REQUEST && handled.add(message.getId())) {
                    // The user approves each call the agent asks to make, once.
                    securityContext.asUser(user, () -> askStroomAIService.answerApproval(
                            new AnswerAiApprovalRequest(chat, message.getId(), true)));
                }
                if (message.getMessageType() == AiMessageType.ELICITATION && handled.add(message.getId())) {
                    final ElicitationRecord form = JsonUtil.readValue(message.getMessage(), ElicitationRecord.class);
                    if (form.status() == ElicitationRecord.Status.PENDING) {
                        final Map<String, String> answer = forms.apply(new Form(message.getId(), form.tool(),
                                form.message(), form.schema()));
                        securityContext.asUser(user, () -> askStroomAIService.answerElicitation(
                                new AnswerAiElicitationRequest(chat, message.getId(), answer == null
                                        ? "DECLINE"
                                        : "ACCEPT", answer)));
                    }
                }
            }
            if (answers(messages) > answersBefore && !runManager.isRunningHere(chat.getId())) {
                final List<AiChatMessage> all = messages(user, chat.getId());
                return List.copyOf(all.subList(Math.min(before, all.size()), all.size()));
            }
            sleep();
        }
        throw new AssertionError("No answer within " + timeout.toSeconds() + " s to: " + question);
    }

    /// @param user   The user.
    /// @param chatId The chat.
    /// @return Its messages, in order.
    public List<AiChatMessage> messages(final UserRef user, final int chatId) {
        return securityContext.asUserResult(user, () -> aiService.getMessages(chatId));
    }

    /// Uses a button a tool call offered (e.g. a proposal's 'Review and apply'), as the user.
    ///
    /// @param user     The user.
    /// @param chatId   The chat.
    /// @param proposal The proposal ([#proposals]).
    /// @return What came of it.
    public AiUserActionResult apply(final UserRef user, final int chatId, final Proposal proposal) {
        return securityContext.asUserResult(user, () -> userActionService.doUserAction(chatId,
                proposal.messageId(), proposal.action().id()));
    }

    /// The tool calls of some messages, in order, each with its result (a call with no result yet has none).
    ///
    /// @param messages The messages.
    /// @return The calls.
    public static List<Call> calls(final List<AiChatMessage> messages) {
        final Map<String, ToolCallRecord> asked = new LinkedHashMap<>();
        final List<Call> calls = new ArrayList<>();
        for (final AiChatMessage message : messages) {
            if (message.getMessageType() == AiMessageType.TOOL_CALL) {
                final ToolCallRecord call = JsonUtil.readValue(message.getMessage(), ToolCallRecord.class);
                asked.put(call.callId(), call);
            } else if (message.getMessageType() == AiMessageType.TOOL_RESULT) {
                final ToolResultRecord result = JsonUtil.readValue(message.getMessage(), ToolResultRecord.class);
                final ToolCallRecord call = asked.get(result.callId());
                calls.add(new Call(message.getId(), result.tool(), call == null
                        ? "{}"
                        : call.arguments(), result.error(), result.text(), result.userActions() == null
                        ? List.of()
                        : List.copyOf(result.userActions())));
            }
        }
        return calls;
    }

    /// The proposals of some messages: each button a `propose_skill` call offered, in order.
    ///
    /// @param messages The messages.
    /// @return The proposals.
    public static List<Proposal> proposals(final List<AiChatMessage> messages) {
        final List<Proposal> proposals = new ArrayList<>();
        for (final Call call : calls(messages)) {
            if ("propose_skill".equals(call.tool())) {
                call.userActions().forEach(action -> proposals.add(new Proposal(call.messageId(), action)));
            }
        }
        return proposals;
    }

    /// @param messages Some messages.
    /// @return The model's replies among them, in order.
    public static List<String> replies(final List<AiChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                   || message.getMessageType() == AiMessageType.INTERIM_RESPONSE)
                .map(AiChatMessage::getMessage)
                .toList();
    }

    /// @param messages Some messages.
    /// @return The errors among them (a run that failed), in order.
    public static List<String> errors(final List<AiChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.ERROR)
                .map(AiChatMessage::getMessage)
                .toList();
    }

    private static long answers(final List<AiChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                   || message.getMessageType() == AiMessageType.ERROR)
                .count();
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        }
    }


    // --------------------------------------------------------------------------------


    /// A form the run stopped for.
    ///
    /// @param messageId Its message.
    /// @param tool      The tool that asked, if known.
    /// @param message   What it says.
    /// @param schema    Its fields, as a flat JSON schema.
    public record Form(int messageId, String tool, String message, Map<String, Object> schema) {

    }

    /// A tool call in the chat and its result.
    ///
    /// @param messageId     Its result's message.
    /// @param tool          The tool.
    /// @param argumentsJson Its arguments, as JSON.
    /// @param error         True if it failed or wasn't run.
    /// @param text          What the model was given of its result.
    /// @param userActions   The buttons it offered the user.
    public record Call(int messageId,
                       String tool,
                       String argumentsJson,
                       boolean error,
                       String text,
                       List<UserActionRecord> userActions) {

        /// @return Its arguments.
        @SuppressWarnings("unchecked")
        public Map<String, Object> arguments() {
            try {
                return argumentsJson == null || argumentsJson.isBlank()
                        ? Map.of()
                        : JsonUtil.readValue(argumentsJson, Map.class);
            } catch (final RuntimeException e) {
                return Map.of();
            }
        }
    }

    /// A button a `propose_skill` call offered.
    ///
    /// @param messageId The call's result's message.
    /// @param action    The button: `apply_documentation_change` (its `doc` the skills doc, its `request` the doc's
    ///                  new text) or `create_instructions` (a new SKILLS doc, its `request` the text).
    public record Proposal(int messageId, UserActionRecord action) {

    }
}
