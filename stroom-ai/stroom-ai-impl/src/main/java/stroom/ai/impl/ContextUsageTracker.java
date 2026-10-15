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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Records how many tokens each model call in a chat used, as reported by the model, and keeps what is needed to
/// estimate how much of the context window a chat uses between calls:
///
/// - how far off the simple estimate (characters / 4) was for the chat's last call, used to correct estimates;
/// - how many tokens the tool definitions offered in the last call took.
///
/// The correction is kept in memory on the node that made the call; on another node estimates are uncorrected
/// until a call is made there.
@Singleton
public class ContextUsageTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContextUsageTracker.class);
    private static final SimpleTokenCountEstimator ESTIMATOR = new SimpleTokenCountEstimator();

    private final AiDao aiDao;
    private final AiChatRunManager runManager;
    private final Map<Integer, Double> corrections = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> toolDefinitionTokens = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> serverInstructionTokens = new ConcurrentHashMap<>();

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
        final int toolTokens = estimateTools(tools);
        toolDefinitionTokens.put(chatId, toolTokens);
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

    /// Forgets what is held for a chat, e.g. because it has been deleted.
    public void forget(final int chatId) {
        corrections.remove(chatId);
        toolDefinitionTokens.remove(chatId);
        serverInstructionTokens.remove(chatId);
    }
}
