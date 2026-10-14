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
import stroom.ai.impl.ToolApprovalRequest.Decision;
import stroom.ai.impl.ToolApprovalRequest.PendingCall;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.ToolRunContext;
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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
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
     * Chat messages holding content from tools or Stroom data, which could hold instructions planted by someone
     * other than the user.
     */
    private static final Set<AiMessageType> UNTRUSTED_MESSAGE_TYPES = Set.of(
            AiMessageType.TOOL_RESULT,
            AiMessageType.ATTACHMENT,
            AiMessageType.DASHBOARD_DATA,
            AiMessageType.QUERY_DATA,
            AiMessageType.TABLE_DATA);

    /**
     * How many of the latest tool results are kept as they are when the conversation is getting too large.
     */
    static final int RECENT_RESULTS_KEPT = 2;
    /**
     * Tool results shorter than this are never shortened.
     */
    static final int SHORTENED_MIN_CHARS = 300;
    /**
     * Put in place of a tool result that has been shortened.
     */
    static final String SHORTENED_RESULT = "[This result has been removed to save space in the context window. "
                                           + "It is stored: call read_turn (with no turn id, for this turn) to "
                                           + "read it again if you need it.]";

    /**
     * How many times the model may be asked again because the user sent a message just as it finished.
     */
    private static final int MAX_STEER_FOLLOW_UPS = 3;

    private final AiService aiService;
    private final BuiltInTools builtInTools;
    private final AiChatRunManager runManager;
    private final WorkspaceStore workspaceStore;
    private final ContextUsageTracker usageTracker;
    private final ToolConsent toolConsent;
    private final AiAuditLog auditLog;
    private final ModelStreamer modelStreamer;
    private final ToolGroups.Activations toolGroupActivations;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    McpAgentChat(final AiService aiService,
                 final BuiltInTools builtInTools,
                 final AiChatRunManager runManager,
                 final WorkspaceStore workspaceStore,
                 final ContextUsageTracker usageTracker,
                 final ToolConsent toolConsent,
                 final AiAuditLog auditLog,
                 final ModelStreamer modelStreamer,
                 final ToolGroups.Activations toolGroupActivations,
                 final Provider<AiConfig> aiConfigProvider) {
        this.aiService = aiService;
        this.toolGroupActivations = toolGroupActivations;
        this.aiConfigProvider = aiConfigProvider;
        this.modelStreamer = modelStreamer;
        this.builtInTools = builtInTools;
        this.runManager = runManager;
        this.workspaceStore = workspaceStore;
        this.usageTracker = usageTracker;
        this.toolConsent = toolConsent;
        this.auditLog = auditLog;
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
                      final int steerAfterMessageId,
                      final ContextBudget budget) {
        // Built-in tools that need the model (e.g. to summarise) find it here.
        return new ToolRunContext(chatModel, modelDoc, cancelled::get).run(() -> chatWithTools(chatModel, modelDoc,
                chatId, messages, toolset, workingMessageId, cancelled, taskContext, attempt, debugLog,
                steerAfterMessageId, budget));
    }

    private ChatResponse chatWithTools(final ChatModel chatModel,
                                       final OpenAIModelDoc modelDoc,
                                       final int chatId,
                                       final List<ChatMessage> messages,
                                       final McpToolset toolset,
                                       final int workingMessageId,
                                       final AtomicBoolean cancelled,
                                       final TaskContext taskContext,
                                       final int attempt,
                                       final StringBuilder debugLog,
                                       final int steerAfterMessageId,
                                       final ContextBudget budget) {
        final SteerFeed steerFeed = new SteerFeed(chatId, steerAfterMessageId);
        // Anything in the chat that came from tools or Stroom data could hold planted instructions.
        if (aiService.getMessages(chatId).stream()
                .anyMatch(m -> UNTRUSTED_MESSAGE_TYPES.contains(m.getMessageType()))) {
            runManager.markTainted(chatId);
        }
        final Map<ToolSpecification, ToolExecutor> builtIns = new LinkedHashMap<>();
        final Map<ToolSpecification, ToolExecutor> mcpTools = new LinkedHashMap<>();
        if (modelDoc == null || modelDoc.isToolsEnabled()) {
            builtIns.putAll(builtInTools.toolMap());
            addMcpTools(mcpTools, toolset, builtIns.keySet());
        }
        // A large set of MCP tools is offered as groups the model turns on when it needs them.
        final ToolGroups toolGroups = ToolGroups.of(mcpTools, toolset, toolGroupThreshold());
        Map<ToolSpecification, ToolExecutor> tools = offeredTools(chatId, builtIns, toolGroups, modelDoc);
        final List<ChatMessage> conversation = withUnavailableServersNote(messages, toolset.getWarnings());
        if (tools.isEmpty()) {
            LOGGER.debug(() -> "chat: no tools available for chatId=" + chatId + ", making a plain call");
            ChatResponse response = call(chatModel, conversation, null, chatId, modelDoc, budget);
            // If the user added something while the model worked, answer again with it.
            for (int i = 0; i < MAX_STEER_FOLLOW_UPS && steerFeed.addTo(conversation, response.aiMessage()); i++) {
                response = call(chatModel, conversation, null, chatId, modelDoc, budget);
            }
            return response;
        }

        // Carry on from calls waiting for approval when the run last stopped, now they have been answered.
        resumePending(chatId, conversation, tools, debugLog);


        ChatResponse response = null;
        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            if (cancelled.get() || taskContext.isTerminated()) {
                throw new TaskTerminatedException();
            }
            // Pick up anything the user has added since the last model call, and any group of tools the model has
            // turned on.
            if (iteration > 1) {
                conversation.addAll(steerFeed.poll());
                tools = offeredTools(chatId, builtIns, toolGroups, modelDoc);
            }
            final List<ToolSpecification> toolSpecifications = List.copyOf(tools.keySet());
            LOGGER.debug(() -> "chat: chatId=" + chatId + " offering " + toolSpecifications.size()
                               + " tool(s): " + toolSpecifications.stream()
                                       .map(ToolSpecification::name)
                                       .collect(Collectors.joining(", ")));

            final List<ToolSpecification> offered = iteration < MAX_ITERATIONS
                    ? toolSpecifications
                    : null;

            final int currentIteration = iteration;
            response = LOGGER.logDurationIfDebugEnabled(
                    () -> call(chatModel, conversation, offered, chatId, modelDoc, budget),
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
            final List<PendingCall> pending = new ArrayList<>();
            final List<ToolConsent.Verdict> askVerdicts = new ArrayList<>();
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
                final boolean unattended = runManager.isUnattended(chatId);
                // Turning on a group of tools changes nothing, so needs no approval.
                final ToolConsent.Verdict verdict = ToolGroups.isActivationTool(toolRequest.name())
                                                    && toolGroups.isGrouped()
                        ? ToolConsent.Verdict.RUN
                        : toolConsent.decide(chatId, toolRequest.name(), builtInTools, toolset, unattended);
                switch (verdict.kind()) {
                    case RUN -> conversation.add(ToolExecutionResultMessage.from(toolRequest,
                            runAndRecord(chatId, callMessage, toolRequest, tools, debugLog, t + 1, unattended)));
                    case DENY -> conversation.add(ToolExecutionResultMessage.from(toolRequest,
                            recordNotRun(chatId, toolRequest, "Not run: " + verdict.reason(), unattended,
                                    "Not allowed in this chat")));
                    case ASK -> {
                        LOGGER.debug(() -> "Tool '" + toolRequest.name() + "' in chatId=" + chatId
                                           + " needs approval: " + verdict.reason());
                        pending.add(pendingCall(toolRequest, toolset, verdict));
                        askVerdicts.add(verdict);
                    }
                }
            }
            if (!pending.isEmpty()) {
                // Stop here, giving up the thread and the slot, until the user answers. The run carries on from
                // the stored calls (see resumePending).
                aiService.storeMessage(chatId, AiMessageType.APPROVAL_REQUEST,
                        JsonUtil.writeValueAsString(approvalRequest(pending, askVerdicts), false));
                throw new AiChatRunManager.WaitingForUserException(pending.size() + " tool call(s) in chat "
                                                                   + chatId + " need approval");
            }
        }

        // Loop cap hit. Make one final call with no tools so the model has to answer in words.
        LOGGER.info(() -> "chat: hit the tool iteration cap for chatId=" + chatId);
        if (response != null && response.aiMessage().hasToolExecutionRequests()) {
            return call(chatModel, conversation, null, chatId, modelDoc, budget);
        }
        return response;
    }

    /**
     * Runs a tool call, records its result (saving a large result to the workspace, and giving the model a stub),
     * and audits it.
     *
     * @return What the model is given.
     */
    private String runAndRecord(final int chatId,
                                final AiChatMessage callMessage,
                                final ToolExecutionRequest toolRequest,
                                final Map<ToolSpecification, ToolExecutor> tools,
                                final StringBuilder debugLog,
                                final int callNo,
                                final boolean unattended) {
        if (builtInTools.readsUntrustedContent(toolRequest.name())) {
            runManager.markTainted(chatId);
        }
        final long startMs = System.currentTimeMillis();
        final String result = execute(tools, toolRequest, toolRequest.name(), chatId);
        final long durationMs = System.currentTimeMillis() - startMs;
        runManager.addUsage(chatId, 0, 0, 1);
        LOGGER.trace(() -> "Tool '" + toolRequest.name() + "' result:\n" + result);
        appendDebugToolCall(debugLog, callNo, toolRequest, result);

        // A large result is saved to the workspace and the model is given a stub, so it does not fill the
        // model's context.
        final String path = ToolOutputs.isLarge(result) && !builtInTools.boundsItsOutput(toolRequest.name())
                ? saveOutput(chatId, callMessage, result)
                : null;
        final String forModel = path != null
                ? ToolOutputs.stub(result, path)
                : result;
        final boolean error = isError(result);
        aiService.storeMessage(chatId, AiMessageType.TOOL_RESULT,
                JsonUtil.writeValueAsString(new ToolResultRecord(toolRequest.id(), toolRequest.name(),
                        error, forModel, path, result.length(), durationMs), false));
        auditLog.toolCall(chatId, toolRequest.name(), toolRequest.arguments(), error
                ? "Failed"
                : "Run", !error, unattended);
        return forModel;
    }

    /**
     * Records that a tool call was not run, and audits it.
     *
     * @return What the model is given.
     */
    private String recordNotRun(final int chatId,
                                final ToolExecutionRequest toolRequest,
                                final String text,
                                final boolean unattended,
                                final String outcome) {
        aiService.storeMessage(chatId, AiMessageType.TOOL_RESULT,
                JsonUtil.writeValueAsString(new ToolResultRecord(toolRequest.id(), toolRequest.name(), true, text,
                        null, text.length(), 0), false));
        auditLog.toolCall(chatId, toolRequest.name(), toolRequest.arguments(), outcome, false, unattended);
        return text;
    }

    /**
     * Finishes the tool calls of the current turn that were waiting for the user's approval when the run last
     * stopped: those allowed are run, and the model is told about the rest.
     */
    private void resumePending(final int chatId,
                               final List<ChatMessage> conversation,
                               final Map<ToolSpecification, ToolExecutor> tools,
                               final StringBuilder debugLog) {
        final List<AiChatMessage> history = aiService.getMessages(chatId);
        int question = history.size() - 1;
        while (question >= 0 && history.get(question).getMessageType() != AiMessageType.USER_MESSAGE) {
            question--;
        }
        if (question < 0) {
            return;
        }
        final List<AiChatMessage> turn = history.subList(question + 1, history.size());
        final Set<String> answered = new HashSet<>();
        ToolApprovalRequest approval = null;
        for (final AiChatMessage message : turn) {
            if (message.getMessageType() == AiMessageType.TOOL_RESULT) {
                NullSafe.consume(read(message, ToolResultRecord.class), result -> answered.add(result.callId()));
            } else if (message.getMessageType() == AiMessageType.APPROVAL_REQUEST) {
                final ToolApprovalRequest request = read(message, ToolApprovalRequest.class);
                if (request != null && ToolApprovalRequest.KIND.equals(request.kind())) {
                    approval = request;
                }
            }
        }
        int callNo = 0;
        for (final AiChatMessage message : turn) {
            if (message.getMessageType() != AiMessageType.TOOL_CALL) {
                continue;
            }
            final ToolCallRecord call = read(message, ToolCallRecord.class);
            if (call == null || answered.contains(call.callId())) {
                continue;
            }
            final ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id(call.callId())
                    .name(call.tool())
                    .arguments(call.arguments())
                    .build();
            conversation.add(AiMessage.from(List.of(request)));
            final PendingCall decided = approval == null
                    ? null
                    : approval.calls().stream()
                            .filter(pendingCall -> call.callId().equals(pendingCall.callId()))
                            .findFirst()
                            .orElse(null);
            final boolean unattended = runManager.isUnattended(chatId);
            final String result;
            if (decided != null && decided.decision() == Decision.ALLOWED) {
                LOGGER.debug(() -> "Running approved tool '" + call.tool() + "' in chatId=" + chatId);
                result = runAndRecord(chatId, message, request, tools, debugLog, ++callNo, unattended);
            } else if (decided != null && decided.decision() == Decision.DENIED) {
                result = recordNotRun(chatId, request, "Not run: the user did not allow this call"
                                                       + (NullSafe.isNonBlankString(decided.note())
                        ? ". They said: " + decided.note()
                        : "."), unattended, "Denied by the user");
            } else {
                result = recordNotRun(chatId, request, "Not run: it was not approved in time, or the run stopped "
                                                       + "before it ran. Check the current state before trying it "
                                                       + "again.", unattended, "Not approved");
            }
            conversation.add(ToolExecutionResultMessage.from(request, result));
        }
    }

    private static PendingCall pendingCall(final ToolExecutionRequest toolRequest,
                                           final McpToolset toolset,
                                           final ToolConsent.Verdict verdict) {
        final McpToolInfo info = toolset.toolInfo(toolRequest.name()).orElse(null);
        return new PendingCall(
                toolRequest.id(),
                toolRequest.name(),
                NullSafe.get(info, McpToolInfo::serverKey),
                NullSafe.get(info, McpToolInfo::toolName),
                toolRequest.arguments(),
                verdict.risks(),
                NullSafe.get(info, McpToolInfo::hash),
                NullSafe.get(info, McpToolInfo::serverHash),
                null,
                null);
    }

    static ToolApprovalRequest approvalRequest(final List<PendingCall> pending,
                                               final List<ToolConsent.Verdict> verdicts) {
        final Set<String> reasons = verdicts.stream()
                .map(ToolConsent.Verdict::reason)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ToolApprovalRequest(
                ToolApprovalRequest.KIND,
                ToolApprovalRequest.Status.PENDING,
                reasons.size() == 1
                        ? reasons.iterator().next()
                        : String.join(" ", reasons),
                pending,
                verdicts.stream().allMatch(ToolConsent.Verdict::allowForChat),
                verdicts.stream().allMatch(ToolConsent.Verdict::allowServerReadOnly));
    }

    private static <T> T read(final AiChatMessage message, final Class<T> type) {
        try {
            return JsonUtil.readValue(message.getMessage(), type);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read message " + message.getId(), e);
            return null;
        }
    }

    /**
     * Calls the model, recording the tokens it reports using. If the conversation is getting too large for the
     * model's context window, earlier tool results in it are shortened first (they are all stored, and the model
     * can read them again with read_turn). If the model says the request is too large anyway, they are all
     * shortened and the call is made once more.
     *
     * @param tools The tools to offer, or null for none.
     */
    private ChatResponse call(final ChatModel chatModel,
                              final List<ChatMessage> conversation,
                              final List<ToolSpecification> tools,
                              final int chatId,
                              final OpenAIModelDoc modelDoc,
                              final ContextBudget budget) {
        if (budget.isOver(usageTracker.estimate(chatId, conversation, tools))) {
            final int shortened = shortenToolResults(conversation, RECENT_RESULTS_KEPT);
            LOGGER.debug(() -> "Shortened " + shortened + " tool result(s) of chatId=" + chatId
                               + " to keep within the context window");
        }
        try {
            return callOnce(chatModel, conversation, tools, chatId, modelDoc);
        } catch (final RuntimeException e) {
            if (!ChatCompactor.isContextOverflowError(e) || shortenToolResults(conversation, 1) == 0) {
                throw e;
            }
            LOGGER.info(() -> "The request for chatId=" + chatId + " was too large; shortened earlier tool "
                              + "results and trying again");
            return callOnce(chatModel, conversation, tools, chatId, modelDoc);
        }
    }

    private ChatResponse callOnce(final ChatModel chatModel,
                                  final List<ChatMessage> conversation,
                                  final List<ToolSpecification> tools,
                                  final int chatId,
                                  final OpenAIModelDoc modelDoc) {
        final ChatRequest.Builder builder = ChatRequest.builder().messages(List.copyOf(conversation));
        if (tools != null) {
            builder.toolSpecifications(tools);
        }
        final ChatRequest request = builder.build();
        final boolean showReasoning = modelDoc == null || modelDoc.isShowReasoning();
        final StreamingChatModel streamingModel = modelDoc != null
                ? aiService.getStreamingChatModel(modelDoc)
                : null;
        final long startMs = System.currentTimeMillis();
        final ChatResponse response;
        long thinkingMs = 0;
        if (streamingModel != null) {
            // Stream the answer, so the user can watch it being written.
            final BooleanSupplier cancelled = ToolRunContext.current()
                    .map(ToolRunContext::getCancelled)
                    .orElse(() -> false);
            final ModelStreamer.StreamedResponse streamed = modelStreamer.chat(streamingModel, request, chatId,
                    showReasoning, cancelled);
            response = streamed.response();
            thinkingMs = streamed.thinkingMs();
        } else {
            response = chatModel.chat(request);
        }
        usageTracker.record(chatId, modelDoc, request.messages(), request.toolSpecifications(), response);
        if (showReasoning) {
            storeThinking(chatId, response, thinkingMs > 0
                    ? thinkingMs
                    : System.currentTimeMillis() - startMs);
        }
        return response;
    }

    /**
     * Stores the model's reasoning for a call, if it gave any, ahead of the tool calls or answer it led to.
     */
    private void storeThinking(final int chatId, final ChatResponse response, final long durationMs) {
        final String thinking = NullSafe.get(response, ChatResponse::aiMessage, AiMessage::thinking);
        if (NullSafe.isBlankString(thinking)) {
            return;
        }
        try {
            aiService.storeMessage(chatId, AiMessageType.THINKING,
                    JsonUtil.writeValueAsString(new ThinkingRecord(thinking, durationMs), false));
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Unable to store the model's reasoning for chatId=" + chatId, e);
        }
    }

    /**
     * Replaces the text of the tool results in the conversation, except the latest ones, with a note saying
     * how to read them again. Short results are left as they are.
     *
     * @param conversation The conversation, changed in place.
     * @param keep         How many of the latest tool results to leave as they are.
     * @return How many results were shortened.
     */
    static int shortenToolResults(final List<ChatMessage> conversation, final int keep) {
        final List<Integer> resultIndexes = new ArrayList<>();
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i) instanceof ToolExecutionResultMessage) {
                resultIndexes.add(i);
            }
        }
        int shortened = 0;
        for (int i = 0; i < resultIndexes.size() - keep; i++) {
            final int index = resultIndexes.get(i);
            final ToolExecutionResultMessage result = (ToolExecutionResultMessage) conversation.get(index);
            if (NullSafe.string(result.text()).length() > SHORTENED_MIN_CHARS) {
                conversation.set(index, ToolExecutionResultMessage.from(result.id(), result.toolName(),
                        SHORTENED_RESULT));
                shortened++;
            }
        }
        return shortened;
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
    private void addMcpTools(final Map<ToolSpecification, ToolExecutor> tools,
                             final McpToolset toolset,
                             final Set<ToolSpecification> builtIns) {
        if (toolset.isEmpty()) {
            return;
        }
        final Set<String> names = builtIns.stream()
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
     * @return The tools to offer the model now: the built-in tools, then the MCP tools, as groups if there are
     * many of them, within the model's tool budget.
     */
    private Map<ToolSpecification, ToolExecutor> offeredTools(final int chatId,
                                                              final Map<ToolSpecification, ToolExecutor> builtIns,
                                                              final ToolGroups toolGroups,
                                                              final OpenAIModelDoc modelDoc) {
        final Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>(builtIns);
        tools.putAll(toolGroups.offered(toolGroupActivations.active(chatId),
                group -> toolGroupActivations.activate(chatId, group)));
        limitTools(tools, NullSafe.getOrElse(modelDoc, OpenAIModelDoc::getMaxToolDefinitions, 0), chatId);
        return tools;
    }

    private int toolGroupThreshold() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getToolGroupThreshold,
                AiConfig.DEFAULT_TOOL_GROUP_THRESHOLD);
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
