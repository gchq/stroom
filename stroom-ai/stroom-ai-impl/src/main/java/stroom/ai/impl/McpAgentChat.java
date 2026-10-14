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
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.openai.shared.OpenAIModelDoc;
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
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Put before a message the user sent while the model was working, so the model knows when it was said.
     */
    static final String STEER_PREFIX = "[Sent by the user while you were working] ";

    /**
     * How many times the model may be asked again because the user sent a message just as it finished.
     */
    private static final int MAX_STEER_FOLLOW_UPS = 3;

    /**
     * Given to the model instead of running a tool that could change something while the user is away.
     */
    static final String UNATTENDED_REFUSAL = "Not run: the user is away, and this tool could change something, "
                                             + "so it needs them to be here. Carry on with read-only tools, and "
                                             + "tell the user what you wanted to do so they can do it when they "
                                             + "are back.";

    private final AiService aiService;
    private final BuiltInTools builtInTools;
    private final AiChatRunManager runManager;
    private final WorkspaceStore workspaceStore;
    private final ContextUsageTracker usageTracker;

    @Inject
    McpAgentChat(final AiService aiService,
                 final BuiltInTools builtInTools,
                 final AiChatRunManager runManager,
                 final WorkspaceStore workspaceStore,
                 final ContextUsageTracker usageTracker) {
        this.aiService = aiService;
        this.builtInTools = builtInTools;
        this.runManager = runManager;
        this.workspaceStore = workspaceStore;
        this.usageTracker = usageTracker;
    }

    ChatResponse chat(final ChatModel chatModel,
                      final OpenAIModelDoc modelDoc,
                      final int chatId,
                      final List<ChatMessage> messages,
                      final McpToolset toolset,
                      final int workingMessageId,
                      final AtomicBoolean cancelled,
                      final TaskContext taskContext,
                      final int attempt,
                      final StringBuilder debugLog,
                      final int steerAfterMessageId) {
        final SteerFeed steerFeed = new SteerFeed(chatId, steerAfterMessageId);
        final Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        if (modelDoc == null || modelDoc.isToolsEnabled()) {
            tools.putAll(builtInTools.toolMap());
            addMcpTools(tools, toolset);
            limitTools(tools, NullSafe.getOrElse(modelDoc, OpenAIModelDoc::getMaxToolDefinitions, 0), chatId);
        }
        final List<ChatMessage> conversation = withUnavailableServersNote(messages, toolset.getWarnings());
        if (tools.isEmpty()) {
            LOGGER.debug(() -> "chat: no tools available for chatId=" + chatId + ", making a plain call");
            ChatResponse response = call(chatModel, ChatRequest.builder().messages(conversation).build(),
                    chatId, modelDoc);
            // If the user added something while the model worked, answer again with it.
            for (int i = 0; i < MAX_STEER_FOLLOW_UPS && steerFeed.addTo(conversation, response.aiMessage()); i++) {
                response = call(chatModel, ChatRequest.builder().messages(conversation).build(), chatId, modelDoc);
            }
            return response;
        }

        final List<ToolSpecification> toolSpecifications = List.copyOf(tools.keySet());
        LOGGER.debug(() -> "chat: chatId=" + chatId + " offering " + toolSpecifications.size()
                           + " tool(s): " + toolSpecifications.stream()
                                   .map(ToolSpecification::name)
                                   .collect(Collectors.joining(", ")));

        ChatResponse response = null;
        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            if (cancelled.get() || taskContext.isTerminated()) {
                throw new TaskTerminatedException();
            }
            // Pick up anything the user has added since the last model call.
            if (iteration > 1) {
                conversation.addAll(steerFeed.poll());
            }

            final boolean offerTools = iteration < MAX_ITERATIONS;
            final ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(conversation);
            if (offerTools) {
                requestBuilder.toolSpecifications(toolSpecifications);
            }

            final int currentIteration = iteration;
            response = LOGGER.logDurationIfDebugEnabled(
                    () -> call(chatModel, requestBuilder.build(), chatId, modelDoc),
                    r -> "chat chatId=" + chatId + " iteration=" + currentIteration
                         + " toolCalls=" + r.aiMessage().toolExecutionRequests().size());

            final AiMessage aiMessage = response.aiMessage();
            if (!aiMessage.hasToolExecutionRequests()) {
                // If the user added something while the model worked, carry on with it.
                if (iteration < MAX_ITERATIONS && steerFeed.addTo(conversation, aiMessage)) {
                    continue;
                }
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

                final AiChatMessage callMessage = aiService.storeMessage(chatId, AiMessageType.TOOL_CALL,
                        JsonUtil.writeValueAsString(new ToolCallRecord(toolRequest.id(), toolRequest.name(),
                                toolRequest.arguments(), aiMessage.text()), false));
                final long startMs = System.currentTimeMillis();
                final String result;
                final boolean refused;
                if (runManager.isUnattended(chatId)
                    && !builtInTools.isReadOnly(toolRequest.name())
                    && !toolset.isReadOnly(toolRequest.name())) {
                    // The user has gone, so only read-only tools may run.
                    LOGGER.info(() -> "Not running tool '" + toolRequest.name() + "' for chatId=" + chatId
                                      + " as the run is unattended");
                    result = UNATTENDED_REFUSAL;
                    refused = true;
                } else {
                    result = execute(tools, toolRequest, toolRequest.name(), chatId);
                    refused = false;
                }
                final long durationMs = System.currentTimeMillis() - startMs;
                runManager.addUsage(chatId, 0, 0, 1);
                LOGGER.trace(() -> "Tool '" + toolRequest.name() + "' result:\n" + result);
                appendDebugToolCall(debugLog, t + 1, toolRequest, result);

                // A large result is saved to the workspace and the model is given a stub, so it does not fill the
                // model's context.
                final String path = ToolOutputs.isLarge(result)
                        ? saveOutput(chatId, callMessage, result)
                        : null;
                final String forModel = path != null
                        ? ToolOutputs.stub(result, path)
                        : result;
                aiService.storeMessage(chatId, AiMessageType.TOOL_RESULT,
                        JsonUtil.writeValueAsString(new ToolResultRecord(toolRequest.id(), toolRequest.name(),
                                refused || isError(result), forModel, path, result.length(), durationMs), false));
                conversation.add(ToolExecutionResultMessage.from(toolRequest, forModel));
            }
        }

        // Loop cap hit. Make one final call with no tools so the model has to answer in words.
        LOGGER.info(() -> "chat: hit the tool iteration cap for chatId=" + chatId);
        if (response != null && response.aiMessage().hasToolExecutionRequests()) {
            return call(chatModel, ChatRequest.builder().messages(conversation).build(), chatId, modelDoc);
        }
        return response;
    }

    /**
     * Calls the model, recording the tokens it reports using.
     */
    private ChatResponse call(final ChatModel chatModel,
                              final ChatRequest request,
                              final int chatId,
                              final OpenAIModelDoc modelDoc) {
        final ChatResponse response = chatModel.chat(request);
        usageTracker.record(chatId, modelDoc, request.messages(), request.toolSpecifications(), response);
        return response;
    }

    /**
     * Saves a large tool output to the chat's workspace.
     *
     * @return The workspace path, or null if it could not be saved (the model is then given it in full).
     */
    private String saveOutput(final int chatId, final AiChatMessage callMessage, final String output) {
        final String path = ToolOutputs.outputPath(callMessage.getTurnId(), callMessage.getId());
        try {
            workspaceStore.createFile(chatId, AiAttachmentType.TOOL_OUTPUT, path, callMessage.getId(), output);
            return path;
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Unable to save tool output to " + path + ": " + e.getMessage(), e);
            return null;
        }
    }

    private static boolean isError(final String result) {
        return result != null && (result.startsWith("Error") || result.startsWith(McpToolAdapter.ERROR_PREFIX));
    }

    /**
     * Adds the MCP tools/resources for this chat. Built-in tools win if a name is already taken, so a server
     * cannot replace one of Stroom's own tools.
     */
    private void addMcpTools(final Map<ToolSpecification, ToolExecutor> tools, final McpToolset toolset) {
        if (toolset.isEmpty()) {
            return;
        }
        final Set<String> names = tools.keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        toolset.tools().forEach((specification, executor) -> {
            if (names.add(specification.name())) {
                tools.put(specification, executor);
            } else {
                LOGGER.warn(() -> "MCP tool '" + specification.name() + "' has the same name as a built-in "
                                  + "tool and will not be offered");
            }
        });
    }

    /**
     * Leaves out the tools past the model's tool budget, keeping the built-in tools (which come first).
     */
    static void limitTools(final Map<ToolSpecification, ToolExecutor> tools,
                           final int maxToolDefinitions,
                           final int chatId) {
        if (maxToolDefinitions <= 0 || tools.size() <= maxToolDefinitions) {
            return;
        }
        final int total = tools.size();
        final List<ToolSpecification> excess = tools.keySet().stream()
                .skip(maxToolDefinitions)
                .toList();
        excess.forEach(tools::remove);
        LOGGER.info(() -> "chat: chatId=" + chatId + " offering " + maxToolDefinitions + " of " + total
                          + " tools, as that is the model's limit");
    }

    /**
     * @return The conversation, with a note telling the model about any MCP servers that are unavailable, so
     * it does not assume their tools exist.
     */
    static List<ChatMessage> withUnavailableServersNote(final List<ChatMessage> messages,
                                                        final List<String> warnings) {
        final List<ChatMessage> conversation = new ArrayList<>(messages);
        if (!warnings.isEmpty()) {
            final String note = "Note: some MCP tool servers are unavailable for this question, so their tools "
                                + "cannot be used:\n- " + String.join("\n- ", warnings);
            conversation.add(Math.min(1, conversation.size()), SystemMessage.from(note));
        }
        return conversation;
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

    /**
     * Reads the messages the user sends to steer a run while it works.
     */
    private final class SteerFeed {

        private final int chatId;
        private int lastMessageId;

        private SteerFeed(final int chatId, final int lastMessageId) {
            this.chatId = chatId;
            this.lastMessageId = lastMessageId;
        }

        /**
         * @return Messages the user has sent since the last time, for the model.
         */
        List<UserMessage> poll() {
            final List<UserMessage> steers = new ArrayList<>();
            for (final AiChatMessage message : aiService.getMessagesSince(chatId, lastMessageId)) {
                lastMessageId = Math.max(lastMessageId, message.getId());
                if (message.getMessageType() == AiMessageType.STEER_MESSAGE) {
                    LOGGER.debug(() -> "Picked up a steering message for chatId=" + chatId);
                    steers.add(UserMessage.from(STEER_PREFIX + message.getMessage()));
                }
            }
            return steers;
        }

        /**
         * Adds the model's answer and the user's new messages to the conversation, if there are any new
         * messages.
         *
         * @return True if there were new messages, so the model should be asked again.
         */
        boolean addTo(final List<ChatMessage> conversation, final AiMessage answer) {
            final List<UserMessage> steers = poll();
            if (steers.isEmpty()) {
                return false;
            }
            conversation.add(answer);
            conversation.addAll(steers);
            return true;
        }
    }
}
