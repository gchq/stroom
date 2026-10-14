/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.ai.impl.tools.BuiltInTools;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Runs a bounded tool-calling loop for a chat question. The model is offered the built-in tools (see
 * {@link BuiltInTools}) plus any MCP tools/resources from the model's configured tool servers; any tool
 * calls it makes are executed and fed back, and the loop repeats until the model answers without calling
 * a tool, the iteration cap is hit, or the task is stopped. If there are no tools at all it is just a
 * plain single call.
 * <p>
 * While it runs it keeps the chat's WORKING message updated with a short line explaining <em>why</em>
 * each tool is being called (the model's own rationale where it gave one, otherwise the tool name and
 * its most telling argument), and — when debug detail is enabled — records the full rationale, arguments
 * and (truncated) results into the persistent request detail.
 */
class McpAgentChat {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpAgentChat.class);

    /**
     * How many model round-trips the loop will make for a single question before it stops offering tools
     * and takes whatever the model says next. A misbehaving model or server cannot make the chat loop
     * forever.
     */
    private static final int MAX_ITERATIONS = 12;

    /**
     * How much of a tool's arguments / result to keep in the persistent request detail before truncating.
     */
    private static final int DEBUG_ARGS_LIMIT = 1_500;
    private static final int DEBUG_RESULT_LIMIT = 3_000;

    /**
     * How long a progress message shown to the user may be.
     */
    private static final int PROGRESS_MESSAGE_LIMIT = 160;

    /**
     * Argument fields, in preference order, whose value best summarises what a tool call is for.
     */
    private static final List<String> HEADLINE_ARG_KEYS = List.of(
            "query", "q", "question", "prompt", "text", "search",
            "uri", "url", "path", "resource", "mcpServer", "index", "name", "id");

    private final AiService aiService;
    private final BuiltInTools builtInTools;

    @Inject
    McpAgentChat(final AiService aiService,
                 final BuiltInTools builtInTools) {
        this.aiService = aiService;
        this.builtInTools = builtInTools;
    }

    ChatResponse chat(final ChatModel chatModel,
                      final int chatId,
                      final List<ChatMessage> messages,
                      final McpToolset toolset,
                      final int workingMessageId,
                      final AtomicBoolean cancelled,
                      final TaskContext taskContext,
                      final int attempt,
                      final StringBuilder debugLog) {
        final Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>(builtInTools.toolMap());
        tools.putAll(mcpTools(toolset, chatId, messages));
        if (tools.isEmpty()) {
            LOGGER.debug(() -> "chat: no tools available for chatId=" + chatId + ", making a plain call");
            return chatModel.chat(messages);
        }

        final List<ToolSpecification> toolSpecifications = List.copyOf(tools.keySet());
        LOGGER.debug(() -> "chat: chatId=" + chatId + " offering " + toolSpecifications.size()
                           + " tool(s): " + toolSpecifications.stream()
                                   .map(ToolSpecification::name)
                                   .collect(Collectors.joining(", ")));

        final List<ChatMessage> conversation = new ArrayList<>(messages);
        ChatResponse response = null;
        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            if (cancelled.get() || taskContext.isTerminated()) {
                throw new TaskTerminatedException();
            }

            final boolean offerTools = iteration < MAX_ITERATIONS;
            final ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(conversation);
            if (offerTools) {
                requestBuilder.toolSpecifications(toolSpecifications);
            }

            final int currentIteration = iteration;
            response = LOGGER.logDurationIfDebugEnabled(
                    () -> chatModel.chat(requestBuilder.build()),
                    r -> "chat chatId=" + chatId + " iteration=" + currentIteration
                         + " toolCalls=" + r.aiMessage().toolExecutionRequests().size());

            final AiMessage aiMessage = response.aiMessage();
            if (!aiMessage.hasToolExecutionRequests()) {
                return response;
            }

            // Whatever the model said alongside its tool calls is its own explanation of what it is
            // trying to find out - the best "why" to show the user and to record.
            final String rationale = concise(aiMessage.text(), PROGRESS_MESSAGE_LIMIT);
            appendDebugRationale(debugLog, attempt, currentIteration, aiMessage.text());

            conversation.add(aiMessage);
            final List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            for (int t = 0; t < toolRequests.size(); t++) {
                if (cancelled.get() || taskContext.isTerminated()) {
                    throw new TaskTerminatedException();
                }
                final ToolExecutionRequest toolRequest = toolRequests.get(t);

                final String progress = toolCallProgressMessage(rationale, toolRequest);
                taskContext.info(() -> progress);
                aiService.updateMessageText(workingMessageId, progress + "…");

                final String result = execute(tools, toolRequest, toolRequest.name(), chatId);
                LOGGER.trace(() -> "Tool '" + toolRequest.name() + "' result:\n" + result);
                appendDebugToolCall(debugLog, t + 1, toolRequest, result);
                conversation.add(ToolExecutionResultMessage.from(toolRequest, result));
            }
        }

        // Loop cap hit. Make one final call with no tools so the model has to answer in words.
        LOGGER.info(() -> "chat: hit the tool iteration cap for chatId=" + chatId);
        if (response != null && response.aiMessage().hasToolExecutionRequests()) {
            return chatModel.chat(ChatRequest.builder().messages(conversation).build());
        }
        return response;
    }

    /**
     * The MCP tools/resources for this chat, or an empty map when the model has no MCP tool servers.
     */
    private Map<ToolSpecification, ToolExecutor> mcpTools(final McpToolset toolset,
                                                         final int chatId,
                                                         final List<ChatMessage> messages) {
        if (toolset.getToolProvider() == null) {
            return Map.of();
        }
        // ToolProviderRequest requires a non-null invocationContext and userMessage even though the MCP
        // tool provider only uses the request to list tools. Supply a minimal context and the last user
        // message from the conversation.
        final ToolProviderResult result = toolset.getToolProvider().provideTools(
                ToolProviderRequest.builder()
                        .invocationContext(InvocationContext.builder()
                                .chatMemoryId(chatId)
                                .timestampNow()
                                .build())
                        .messages(messages)
                        .userMessage(lastUserMessage(messages))
                        .build());
        return result.tools();
    }

    private String execute(final Map<ToolSpecification, ToolExecutor> tools,
                           final ToolExecutionRequest toolRequest,
                           final String toolName,
                           final int chatId) {
        final ToolExecutor executor = tools.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(toolName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (executor == null) {
            return "Error: no such tool '" + toolName + "'";
        }
        try {
            return executor.execute(toolRequest, chatId);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Tool '" + toolName + "' failed for chatId=" + chatId, e);
            return "Error calling tool '" + toolName + "': "
                   + NullSafe.nonBlankStringElse(e.getMessage(), e.toString());
        }
    }

    // ------------------------------------------------------------------------
    // Progress messages shown to the user
    // ------------------------------------------------------------------------

    /**
     * A short, human-readable line explaining why a tool is being run. Prefers the model's own words
     * ({@code rationale}); failing that, names the tool and the most telling argument (a query string, a
     * resource URI, ...).
     */
    private String toolCallProgressMessage(final String rationale, final ToolExecutionRequest toolRequest) {
        final String tool = toolRequest.name();

        if (NullSafe.isNonBlankString(rationale)) {
            final int budget = Math.max(24, PROGRESS_MESSAGE_LIMIT - tool.length() - 4);
            return truncate(rationale, budget) + " (" + tool + ")";
        }
        final String headline = argHeadline(toolRequest.arguments());
        if (headline != null) {
            return truncate("Running " + tool + " — " + headline, PROGRESS_MESSAGE_LIMIT);
        }
        return "Running " + tool;
    }

    /**
     * @return the value of the argument that best summarises the call (e.g. {@code query=errors last 24h}),
     * or null if the arguments are empty / unparseable.
     */
    private String argHeadline(final String argumentsJson) {
        if (NullSafe.isBlankString(argumentsJson)) {
            return null;
        }
        final Map<?, ?> args;
        try {
            args = JsonUtil.readValue(argumentsJson, Map.class);
        } catch (final RuntimeException e) {
            return truncate(argumentsJson.strip(), PROGRESS_MESSAGE_LIMIT);
        }
        if (NullSafe.isEmptyMap(args)) {
            return null;
        }
        for (final String key : HEADLINE_ARG_KEYS) {
            final String value = scalar(args.get(key));
            if (value != null) {
                return key + "=" + value;
            }
        }
        // No obvious headline field - fall back to the first scalar argument, else the argument names.
        for (final Map.Entry<?, ?> entry : args.entrySet()) {
            final String value = scalar(entry.getValue());
            if (value != null) {
                return entry.getKey() + "=" + value;
            }
        }
        return args.keySet().stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String scalar(final Object value) {
        if (value == null || value instanceof Map || value instanceof List) {
            return null;
        }
        final String s = String.valueOf(value);
        return NullSafe.isBlankString(s)
                ? null
                : s;
    }

    /**
     * Collapses whitespace, keeps the first sentence where that is short enough, and truncates to
     * {@code limit} characters.
     */
    private static String concise(final String text, final int limit) {
        if (NullSafe.isBlankString(text)) {
            return null;
        }
        String t = text.strip().replaceAll("\\s+", " ");
        final int firstSentenceEnd = t.indexOf(". ");
        if (firstSentenceEnd > 0 && firstSentenceEnd < limit) {
            t = t.substring(0, firstSentenceEnd + 1);
        }
        return truncate(t, limit);
    }

    private static String truncate(final String text, final int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 1)).strip() + "…";
    }

    // ------------------------------------------------------------------------
    // Persistent request detail (DEBUG_DETAIL)
    // ------------------------------------------------------------------------

    private void appendDebugRationale(final StringBuilder debugLog,
                                      final int attempt,
                                      final int iteration,
                                      final String rationale) {
        if (debugLog == null) {
            return;
        }
        debugLog.append("### Attempt ").append(attempt)
                .append(" — Tool Use (round ").append(iteration).append(")\n\n");
        if (NullSafe.isNonBlankString(rationale)) {
            debugLog.append("**Model rationale:** ").append(rationale.strip()).append("\n\n");
        }
    }

    private void appendDebugToolCall(final StringBuilder debugLog,
                                     final int toolNumber,
                                     final ToolExecutionRequest toolRequest,
                                     final String result) {
        if (debugLog == null) {
            return;
        }
        debugLog.append("#### Tool call ").append(toolNumber).append(": `")
                .append(toolRequest.name()).append("`\n\n")
                .append("**Arguments:**\n```json\n")
                .append(truncate(NullSafe.nonBlankStringElse(toolRequest.arguments(), "{}"), DEBUG_ARGS_LIMIT))
                .append("\n```\n\n")
                .append("**Result:**\n```\n")
                .append(truncate(NullSafe.string(result), DEBUG_RESULT_LIMIT))
                .append("\n```\n\n");
    }

    private static UserMessage lastUserMessage(final List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof final UserMessage userMessage) {
                return userMessage;
            }
        }
        // ToolProviderRequest rejects a null userMessage; the conversation always has one, but stay safe.
        return UserMessage.from(" ");
    }
}
