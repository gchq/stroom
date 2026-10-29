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

import stroom.ai.api.SimpleTokenCountEstimator;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Records how many tokens each model call in a chat used, as reported by the model, and keeps what is needed to
/// estimate how much of the context window a chat uses between calls:
///
/// - how far off the simple estimate (characters / 4) was for the chat's last call, used to correct estimates;
/// - how many tokens the tool definitions offered in the last call took, and which tools took the most;
/// - how many tokens the steps of the question being answered took as the last call sent them, which is fewer
///   than they take as stored once the run has shortened them.
///
/// The correction is kept in memory on the node that made the call; on another node estimates are uncorrected
/// until a call is made there.
@Singleton
public class ContextUsageTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContextUsageTracker.class);
    private static final SimpleTokenCountEstimator ESTIMATOR = new SimpleTokenCountEstimator();
    /// What uncorrected estimates are multiplied by when deciding whether a request will fit: characters / 4
    /// undercounts XML, JSON and code, which tool results are full of.
    static final double UNMEASURED_SAFETY_FACTOR = 1.25;
    /// How much more than the context window a request is taken to have been when the model says it was too
    /// large without saying by how much.
    static final double UNKNOWN_OVERFLOW_FACTOR = 1.1;
    /// The sizes model servers give for a request that was too large, e.g. vLLM's "you requested 140000 tokens
    /// (135904 in the messages, 4096 in the completion)" or "your request has 135904 input tokens", or "prompt is
    /// too long: 135904 tokens > 131072 maximum".
    private static final List<Pattern> OVERFLOW_SIZE_PATTERNS = List.of(
            Pattern.compile("(\\d+) in the messages"),
            Pattern.compile("(\\d+) input tokens"),
            Pattern.compile("too long: (\\d+) tokens"),
            Pattern.compile("requested (\\d+) tokens"));
    /// How many of the tools whose definitions take the most tokens are kept, for the context meter to name.
    static final int LARGEST_TOOLS_KEPT = 5;

    private final AiDao aiDao;
    private final AiChatRunManager runManager;
    private final Map<Integer, Double> corrections = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> toolDefinitionTokens = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> serverInstructionTokens = new ConcurrentHashMap<>();
    private final Map<Integer, MeasuredCall> lastCalls = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> shortenedParts = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Long>> largestTools = new ConcurrentHashMap<>();
    private final Map<Integer, Long> sentStepTokens = new ConcurrentHashMap<>();

    @Inject
    ContextUsageTracker(final AiDao aiDao, final AiChatRunManager runManager) {
        this.aiDao = aiDao;
        this.runManager = runManager;
    }

    /// Records a model call.
    ///
    /// @param chatId   The chat.
    /// @param modelDoc The model called, or null.
    /// @param sent     The messages sent.
    /// @param tools    The tool definitions offered, if any.
    /// @param response The model's response.
    public void record(final int chatId,
                       final OpenAIModelDoc modelDoc,
                       final List<ChatMessage> sent,
                       final List<ToolSpecification> tools,
                       final ChatResponse response) {
        final Map<String, Long> byTool = estimateEachTool(tools);
        final int toolTokens = (int) byTool.values().stream().mapToLong(Long::longValue).sum();
        toolDefinitionTokens.put(chatId, toolTokens);
        largestTools.put(chatId, largest(byTool, LARGEST_TOOLS_KEPT));
        sentStepTokens.put(chatId, estimateSteps(sent));
        LOGGER.debug(() -> "chatId=" + chatId + " offered " + byTool.size() + " tool(s) taking about " + toolTokens
                           + " tokens: " + largest(byTool, byTool.size()));
        final long estimate = ESTIMATOR.estimateTokenCountInMessages(sent) + toolTokens;
        final TokenUsage usage = NullSafe.get(response, ChatResponse::tokenUsage);
        final Integer inputTokens = NullSafe.get(usage, TokenUsage::inputTokenCount);
        if (inputTokens == null || inputTokens <= 0) {
            LOGGER.debug(() -> "No token usage reported for chatId=" + chatId);
            // The model's context window is still worth knowing, to show how full it is from the estimate.
            if (modelDoc != null) {
                aiDao.recordModel(chatId, modelDoc.getUuid(), modelDoc.getMaxContextWindowTokens());
            }
            return;
        }
        final int outputTokens = NullSafe.getOrElse(usage, TokenUsage::outputTokenCount, 0);
        if (estimate > 0) {
            corrections.put(chatId, inputTokens / (double) estimate);
        }
        lastCalls.put(chatId, new MeasuredCall(inputTokens, System.currentTimeMillis()));
        aiDao.recordUsage(chatId,
                NullSafe.get(modelDoc, OpenAIModelDoc::getUuid),
                NullSafe.getOrElse(modelDoc, OpenAIModelDoc::getMaxContextWindowTokens, 0),
                inputTokens,
                outputTokens);
        runManager.addUsage(chatId, inputTokens, outputTokens, 0);
        LOGGER.debug(() -> "chatId=" + chatId + " used " + inputTokens + " input tokens (estimated " + estimate
                           + "), " + outputTokens + " output tokens");
    }

    /// Estimates how many tokens a request would use, corrected by how far off the estimate was for the chat's
    /// last measured call.
    ///
    /// @param chatId   The chat.
    /// @param messages The messages to send.
    /// @param tools    The tool definitions to offer, or null.
    /// @return The estimate.
    public long estimate(final int chatId, final List<ChatMessage> messages, final List<ToolSpecification> tools) {
        final long estimate = ESTIMATOR.estimateTokenCountInMessages(messages) + estimateTools(tools);
        return getCorrection(chatId)
                .map(factor -> Math.round(estimate * factor))
                .orElse(estimate);
    }

    /// As [#estimate(int, List, List)], but erring on the large side when no call has been measured, for deciding
    /// whether a request will fit in the context window.
    ///
    /// @param chatId   The chat.
    /// @param messages The messages to send.
    /// @param tools    The tool definitions to offer, or null.
    /// @return The estimate.
    public long estimateCautiously(final int chatId,
                                   final List<ChatMessage> messages,
                                   final List<ToolSpecification> tools) {
        final long estimate = ESTIMATOR.estimateTokenCountInMessages(messages) + estimateTools(tools);
        return Math.round(estimate * getCorrection(chatId).orElse(UNMEASURED_SAFETY_FACTOR));
    }

    /// Records that the model said a request was too large for its context window, so the chat's estimates are
    /// corrected from what it said the request's size was, or failing that, from the window it was too large for.
    /// Estimates are only ever corrected upwards by this.
    ///
    /// @param chatId        The chat.
    /// @param sent          The messages sent.
    /// @param tools         The tool definitions offered, if any.
    /// @param error         What the model said.
    /// @param contextWindow The model's context window in tokens, or 0 if it is not known.
    /// @return The size the model said the request was, in tokens, if it said.
    public OptionalLong recordOverflow(final int chatId,
                                       final List<ChatMessage> sent,
                                       final List<ToolSpecification> tools,
                                       final Throwable error,
                                       final int contextWindow) {
        final long estimate = ESTIMATOR.estimateTokenCountInMessages(sent) + estimateTools(tools);
        final OptionalLong size = overflowSize(error);
        final double actual = size.isPresent()
                ? size.getAsLong()
                : contextWindow * UNKNOWN_OVERFLOW_FACTOR;
        if (estimate > 0 && actual > 0) {
            corrections.merge(chatId, actual / estimate, Math::max);
        }
        LOGGER.debug(() -> "chatId=" + chatId + " overflowed the context window: estimated " + estimate
                           + ", reported " + size);
        return size;
    }

    /// @param error What the model said when a request was too large.
    /// @return The size it said the request was, in tokens, if it said.
    static OptionalLong overflowSize(final Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            final String message = NullSafe.string(cause.getMessage());
            for (final Pattern pattern : OVERFLOW_SIZE_PATTERNS) {
                final Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    try {
                        return OptionalLong.of(Long.parseLong(matcher.group(1)));
                    } catch (final NumberFormatException e) {
                        // Too many digits to be a size; try the next.
                    }
                }
            }
            cause = cause.getCause() == cause
                    ? null
                    : cause.getCause();
        }
        return OptionalLong.empty();
    }

    /// @param chatId The chat.
    /// @return What to multiply the chat's estimates by to correct them, if a call has been measured on this node.
    public Optional<Double> getCorrection(final int chatId) {
        return Optional.ofNullable(corrections.get(chatId));
    }

    /// @param chatId The chat.
    /// @return An estimate of the tokens taken by the tool definitions offered in the chat's last call.
    public int getToolDefinitionTokens(final int chatId) {
        return toolDefinitionTokens.getOrDefault(chatId, 0);
    }

    /// Records the MCP server instructions given to the model in the chat's latest run, so the context breakdown
    /// can show them.
    ///
    /// @param chatId       The chat.
    /// @param instructions The instructions, or null if there were none.
    public void recordServerInstructions(final int chatId, final String instructions) {
        serverInstructionTokens.put(chatId, ESTIMATOR.estimateTokenCountInText(NullSafe.string(instructions)));
    }

    /// @param chatId The chat.
    /// @return An estimate of the tokens taken by the MCP server instructions in the chat's latest run.
    public int getServerInstructionTokens(final int chatId) {
        return serverInstructionTokens.getOrDefault(chatId, 0);
    }

    /// @param tools Tool definitions, or null.
    /// @return An estimate of the tokens they take.
    static int estimateTools(final List<ToolSpecification> tools) {
        if (NullSafe.isEmptyCollection(tools)) {
            return 0;
        }
        int tokens = 0;
        for (final ToolSpecification tool : tools) {
            tokens += ESTIMATOR.estimateTokenCountInText(tool.toJson());
        }
        return tokens;
    }

    /// @param tools Tool definitions, or null.
    /// @return An estimate of the tokens each takes, by tool name, in the order given.
    static Map<String, Long> estimateEachTool(final List<ToolSpecification> tools) {
        final Map<String, Long> byTool = new LinkedHashMap<>();
        for (final ToolSpecification tool : NullSafe.list(tools)) {
            byTool.merge(tool.name(), (long) ESTIMATOR.estimateTokenCountInText(tool.toJson()), Long::sum);
        }
        return byTool;
    }

    /// @param byTool Tokens by tool name.
    /// @param count  How many to give.
    /// @return The `count` tools that take the most tokens, largest first.
    static Map<String, Long> largest(final Map<String, Long> byTool, final int count) {
        final Map<String, Long> largest = new LinkedHashMap<>();
        byTool.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(count)
                .forEach(entry -> largest.put(entry.getKey(), entry.getValue()));
        return largest;
    }

    /// @param sent The messages of a model call.
    /// @return An estimate of the tokens the question's steps take in them: the model's tool calls, their
    /// results, and the notes earlier steps were put in to save space.
    static long estimateSteps(final List<ChatMessage> sent) {
        final List<ChatMessage> steps = NullSafe.list(sent).stream()
                .filter(message -> (message instanceof final AiMessage ai && ai.hasToolExecutionRequests())
                                   || message instanceof ToolExecutionResultMessage
                                   || isStepsNote(message))
                .toList();
        return steps.isEmpty()
                ? 0
                : ESTIMATOR.estimateTokenCountInMessages(steps);
    }

    private static boolean isStepsNote(final ChatMessage message) {
        return message instanceof final UserMessage user
               && user.hasSingleText()
               && (user.singleText().startsWith(McpAgentChat.TURN_SUMMARY_NOTE)
                   || user.singleText().startsWith(McpAgentChat.COLLAPSED_STEPS_NOTE));
    }

    /// @param chatId The chat.
    /// @return The tools whose definitions took the most tokens in the chat's last call, largest first, with
    /// their tokens.
    public Map<String, Long> getLargestTools(final int chatId) {
        return largestTools.getOrDefault(chatId, Map.of());
    }

    /// @param chatId The chat.
    /// @return An estimate of the tokens the question's steps took in the chat's last call, if one was made.
    public Optional<Long> getSentStepTokens(final int chatId) {
        return Optional.ofNullable(sentStepTokens.get(chatId));
    }

    /// @param chatId The chat.
    /// @return The chat's last model call on this node whose size the model reported, if there is one.
    public Optional<MeasuredCall> getLastCall(final int chatId) {
        return Optional.ofNullable(lastCalls.get(chatId));
    }

    /// Records that a run made its conversation smaller to fit the context window (see `McpAgentChat#shrink`), so
    /// the context meter can say what it sends is less than the chat holds.
    ///
    /// @param chatId The chat.
    /// @param parts  How many parts (tool results, tool call arguments or earlier steps) were shortened.
    public void recordShortened(final int chatId, final int parts) {
        if (parts > 0) {
            shortenedParts.merge(chatId, parts, Integer::sum);
        }
    }

    /// Forgets the parts shortened by the chat's last run, as a new run starts from the stored chat.
    public void resetShortened(final int chatId) {
        shortenedParts.remove(chatId);
    }

    /// @param chatId The chat.
    /// @return How many parts the chat's latest run shortened to fit the context window.
    public int getShortenedParts(final int chatId) {
        return shortenedParts.getOrDefault(chatId, 0);
    }

    /// Forgets what is held for a chat, e.g. because it has been deleted.
    public void forget(final int chatId) {
        corrections.remove(chatId);
        toolDefinitionTokens.remove(chatId);
        serverInstructionTokens.remove(chatId);
        lastCalls.remove(chatId);
        shortenedParts.remove(chatId);
        largestTools.remove(chatId);
        sentStepTokens.remove(chatId);
    }


    // --------------------------------------------------------------------------------


    /// A model call whose size the model reported.
    ///
    /// @param inputTokens The tokens the model said the request had.
    /// @param timeMs      When the call returned.
    public record MeasuredCall(long inputTokens, long timeMs) {

    }
}
