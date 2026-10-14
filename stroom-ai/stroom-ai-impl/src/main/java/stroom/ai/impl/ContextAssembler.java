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
import stroom.ai.api.SimpleTokenCountEstimator;
import stroom.ai.impl.AiDao.ChatUsage;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Builds what the model is sent for a question: the system prompt, any summary of earlier conversation, the
/// conversation history (with attached tables, and a short record of the tools used for each earlier answer),
/// and the question.
///
/// This is the one place the model's view of a chat is built, so that anything else that needs to know what the
/// model is sent (e.g. how much of its context window a chat uses) sees the same thing.
public class ContextAssembler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContextAssembler.class);

    public static final String PART_SYSTEM_PROMPT = "System prompt";
    public static final String PART_TOOL_DEFINITIONS = "Tool definitions";
    public static final String PART_CONVERSATION = "Conversation";
    public static final String PART_ATTACHMENTS = "Attached tables";
    public static final String PART_TOOL_RESULTS = "Tool calls and results";

    /// How much of a tool call's arguments, and of its result, the record of an earlier answer's tools shows.
    static final int TOOL_LOG_ARGS_LIMIT = 200;
    static final int TOOL_LOG_RESULT_LIMIT = 200;

    private static final SimpleTokenCountEstimator ESTIMATOR = new SimpleTokenCountEstimator();

    private final Provider<AskStroomAiConfig> defaultConfigProvider;
    private final WorkspaceStore workspaceStore;
    private final AiService aiService;
    private final AiDao aiDao;
    private final AiWorkspaceDao workspaceDao;
    private final ContextUsageTracker usageTracker;

    @Inject
    ContextAssembler(final Provider<AskStroomAiConfig> defaultConfigProvider,
                     final WorkspaceStore workspaceStore,
                     final AiService aiService,
                     final AiDao aiDao,
                     final AiWorkspaceDao workspaceDao,
                     final ContextUsageTracker usageTracker) {
        this.defaultConfigProvider = defaultConfigProvider;
        this.workspaceStore = workspaceStore;
        this.aiService = aiService;
        this.aiDao = aiDao;
        this.workspaceDao = workspaceDao;
        this.usageTracker = usageTracker;
    }

    /**
     * Selects the stored messages that are sent to the model as conversation history: questions, answers and
     * attachments, up to and including the last question (the one being answered). ERROR rows are left out, as
     * the model would read them as its own earlier replies. Anything after the last question (e.g. a partial
     * answer from an attempt that was stopped) is left out too, so the question can be answered again.
     *
     * @param history All the messages in the chat, in order.
     * @return The messages to send, in the same order.
     */
    public static List<AiChatMessage> selectHistoryForModel(final List<AiChatMessage> history) {
        int end = history.size();
        while (end > 0 && history.get(end - 1).getMessageType() != AiMessageType.USER_MESSAGE) {
            end--;
        }
        final List<AiChatMessage> upToQuestion = end == 0
                ? history
                : history.subList(0, end);
        return upToQuestion.stream()
                .filter(m -> m.getMessageType() == AiMessageType.USER_MESSAGE
                             || m.getMessageType() == AiMessageType.STEER_MESSAGE
                             || m.getMessageType() == AiMessageType.AI_RESPONSE
                             || m.getMessageType() == AiMessageType.ATTACHMENT)
                .toList();
    }

    /**
     * Estimates how much of the model's context window the chat would use if a question were asked now, using
     * the same rules as {@link #assemble}, and corrected by how far off the estimate was for the chat's last
     * measured model call.
     *
     * @param chatId The chat.
     * @return The estimate, with where the tokens go.
     */
    public AiContextUsage estimateUsage(final int chatId) {
        final List<AiChatMessage> history = aiService.getMessages(chatId);
        final Map<String, Long> parts = new LinkedHashMap<>();
        parts.put(PART_SYSTEM_PROMPT, (long) ESTIMATOR.estimateTokenCountInText(systemPrompt()));
        parts.put(PART_TOOL_DEFINITIONS, (long) usageTracker.getToolDefinitionTokens(chatId));

        long conversation = 0;
        long attachments = 0;
        for (final AiChatMessage message : selectHistoryForModel(history)) {
            if (message.getMessageType() == AiMessageType.ATTACHMENT && message.getAttachmentId() != null) {
                attachments += workspaceDao.getSize(message.getAttachmentId()) / 4;
            } else {
                conversation += tokens(message);
            }
        }
        parts.put(PART_CONVERSATION, conversation);
        parts.put(PART_ATTACHMENTS, attachments);

        // Tool calls: in full for the latest turn (if a run is working on it), and as a short log for earlier ones.
        final int latestTurn = history.stream().mapToInt(AiChatMessage::getTurnId).max().orElse(0);
        long toolResults = 0;
        for (final Map.Entry<Integer, List<AiChatMessage>> entry : toolMessagesByTurn(history).entrySet()) {
            if (entry.getKey() == latestTurn) {
                toolResults += entry.getValue().stream().mapToLong(ContextAssembler::tokens).sum();
            } else {
                toolResults += ESTIMATOR.estimateTokenCountInText(NullSafe.string(toolLog(entry.getValue())));
            }
        }
        parts.put(PART_TOOL_RESULTS, toolResults);

        final long estimate = parts.values().stream().mapToLong(Long::longValue).sum();
        final Optional<Double> correction = usageTracker.getCorrection(chatId);
        final long used = correction
                .map(factor -> Math.round(estimate * factor))
                .orElse(estimate);
        final int contextWindow = NullSafe.getOrElse(
                aiDao.getUsage(chatId), ChatUsage::contextWindowTokens, 0);
        return new AiContextUsage(contextWindow, used, correction.isEmpty(), parts);
    }

    private String systemPrompt() {
        return NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAiConfig::getChatSystemPrompt,
                AskStroomAiConfig.DEFAULT_CHAT_SYSTEM_PROMPT);
    }

    private static long tokens(final AiChatMessage message) {
        return message.getTokenCount() != null
                ? message.getTokenCount()
                : ESTIMATOR.estimateTokenCountInText(NullSafe.string(message.getMessage()));
    }

    /**
     * Builds the list of ChatMessages for a unified LLM call. Includes system prompt,
     * optional context summary (from prior trimming), filtered conversation history
     * with inline attachment data, and the current user message.
     *
     * @param maxHistory     maximum number of history messages to include (from the end)
     * @param contextSummary optional summary of earlier messages that were trimmed
     */
    public List<ChatMessage> assemble(final AskStroomAiRequest request,
                                      final int chatId,
                                      final Map<Integer, AiChatAttachment> attachmentMap,
                                      final List<AiChatMessage> relevantHistory,
                                      final int maxHistory,
                                      final String contextSummary) {
        final List<ChatMessage> messages = new ArrayList<>();
        final Map<Integer, List<AiChatMessage>> toolMessagesByTurn = toolMessagesByTurn(chatId);

        // System message.
        messages.add(new SystemMessage(NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAiConfig::getChatSystemPrompt,
                AskStroomAiConfig.DEFAULT_CHAT_SYSTEM_PROMPT)));

        // If we have a summary of earlier (trimmed) conversation, inject it.
        if (contextSummary != null && !contextSummary.isBlank()) {
            messages.add(new UserMessage(
                    "[Prior conversation summary]: " + contextSummary));
            messages.add(new AiMessage(
                    "Understood, I'll keep the prior context in mind."));
        }

        // Add history messages (skip the current user message which was just stored).
        final int startIdx = Math.max(0, relevantHistory.size() - maxHistory - 1);
        for (int i = startIdx; i < relevantHistory.size() - 1; i++) {
            final AiChatMessage msg = relevantHistory.get(i);
            switch (msg.getMessageType()) {
                case ATTACHMENT -> {
                    // Read the attachment's markdown file and inject it as a UserMessage
                    // tagged with the source description.
                    final Integer attachmentId = msg.getAttachmentId();
                    final AiChatAttachment attachment = attachmentId != null
                            ? attachmentMap.get(attachmentId)
                            : null;
                    if (attachment != null) {
                        final Path mdFile = workspaceStore.localFile(attachment.getId());
                        if (Files.exists(mdFile)) {
                            try {
                                final String markdown = Files.readString(mdFile, StandardCharsets.UTF_8);
                                final String description = NullSafe.getOrElse(
                                        attachment, AiChatAttachment::getDescription,
                                        "Attachment " + attachment.getId());
                                final StringBuilder tagged = new StringBuilder();
                                tagged
                                        .append("[Attached Table: ")
                                        .append(description);
                                if (attachment.getRowCount() != null) {
                                    tagged
                                            .append(" (")
                                            .append(attachment.getRowCount())
                                            .append(" rows)");
                                }
                                if (attachment.isTruncated()) {
                                    tagged
                                            .append(" TRUNCATED");
                                }
                                tagged
                                        .append("]\n")
                                        .append(markdown);
                                messages.add(new UserMessage(tagged.toString()));
                            } catch (final IOException e) {
                                LOGGER.warn(() -> "Failed to read attachment file: " + mdFile, e);
                            }
                        }
                    }
                }
                case USER_MESSAGE -> messages.add(new UserMessage(msg.getMessage()));
                case STEER_MESSAGE -> messages.add(new UserMessage(McpAgentChat.STEER_PREFIX + msg.getMessage()));
                case AI_RESPONSE -> {
                    // Say which tools were used for the answer, so the model knows what it has already looked at.
                    final String toolLog = toolLog(toolMessagesByTurn.get(msg.getTurnId()));
                    if (toolLog != null) {
                        messages.add(new SystemMessage(toolLog));
                    }
                    messages.add(new AiMessage(msg.getMessage()));
                }
                default -> LOGGER.debug(() -> "Not sending message of type " + msg.getMessageType());
            }
        }

        // Add the current user message.
        messages.add(new UserMessage(request.getMessage()));

        return messages;
    }


    private Map<Integer, List<AiChatMessage>> toolMessagesByTurn(final int chatId) {
        return toolMessagesByTurn(aiService.getMessages(chatId));
    }

    private static Map<Integer, List<AiChatMessage>> toolMessagesByTurn(final List<AiChatMessage> history) {
        final Map<Integer, List<AiChatMessage>> byTurn = new LinkedHashMap<>();
        for (final AiChatMessage message : history) {
            if (message.getMessageType() == AiMessageType.TOOL_CALL
                || message.getMessageType() == AiMessageType.TOOL_RESULT) {
                byTurn.computeIfAbsent(message.getTurnId(), k -> new ArrayList<>()).add(message);
            }
        }
        return byTurn;
    }

    /// @param toolMessages The TOOL_CALL and TOOL_RESULT messages of a turn, in order, or null.
    /// @return A short record of the tools used in the turn, one line per call, or null if none were.
    static String toolLog(final List<AiChatMessage> toolMessages) {
        if (NullSafe.isEmptyCollection(toolMessages)) {
            return null;
        }
        final Map<String, ToolResultRecord> results = new HashMap<>();
        final List<ToolCallRecord> calls = new ArrayList<>();
        for (final AiChatMessage message : toolMessages) {
            try {
                if (message.getMessageType() == AiMessageType.TOOL_CALL) {
                    calls.add(JsonUtil.readValue(message.getMessage(), ToolCallRecord.class));
                } else {
                    final ToolResultRecord result = JsonUtil.readValue(message.getMessage(), ToolResultRecord.class);
                    results.put(result.callId(), result);
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read tool message " + message.getId(), e);
            }
        }
        if (calls.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder("Tools used for the next answer:");
        for (final ToolCallRecord call : calls) {
            sb.append("\n- ").append(call.tool()).append('(')
                    .append(shorten(NullSafe.string(call.arguments()), TOOL_LOG_ARGS_LIMIT)).append(')');
            final ToolResultRecord result = results.get(call.callId());
            if (result != null) {
                sb.append(" -> ");
                if (result.path() != null) {
                    sb.append("result saved to ").append(result.path());
                } else {
                    sb.append(shorten(NullSafe.string(result.text()), TOOL_LOG_RESULT_LIMIT));
                }
            }
        }
        return sb.toString();
    }

    private static String shorten(final String text, final int limit) {
        final String oneLine = text.replace('\n', ' ').strip();
        return oneLine.length() > limit
                ? oneLine.substring(0, limit - 1) + "…"
                : oneLine;
    }
}
