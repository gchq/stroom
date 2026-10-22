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
import stroom.ai.impl.tools.LangChain4jTools.StroomToolExecutor;
import stroom.ai.impl.tools.TaskListReminder;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.ToolUserAction;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.workspace.WorkspaceArguments;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolCategory;
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
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
     * What the model is told on its last step of a question, when it is no longer offered tools.
     */
    static final String LAST_STEP_NOTE = "You have used all the steps allowed for this question, so you can't "
                                         + "call any more tools now. Without saying you'll do anything else "
                                         + "first, summarise what you have done and found, and what is left "
                                         + "to do. The user can reply 'continue' for you to carry on.";

    /**
     * How much of a tool's arguments / result to keep in the persistent request detail before truncating.
     */
    private static final int DEBUG_ARGS_LIMIT = 1_500;
    private static final int DEBUG_RESULT_LIMIT = 3_000;

    /**
     * How long a progress message shown to the user may be.
     */
    private static final int PROGRESS_MESSAGE_LIMIT = 160;
    // The most of what an MCP server says a tool call has got to that is shown after the working line.
    private static final int PROGRESS_UPDATE_LIMIT = 120;

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
    /// Put after a message the user sent while the model was working, if the tab they attached was sent with it.
    static final String STEER_TAB_NOTE = "[Sent with the attached tab as the user has it now (it may have changed, "
                                         + "e.g. by applying a change you proposed). Tools that read the tab, and "
                                         + "changes you propose, use it now.]";

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
     * How often a running MCP tool call looks to see if the run has been stopped.
     */
    private static final Duration STOP_CHECK_INTERVAL = Duration.ofMillis(200);
    /// How often a tool call from an MCP server checks whether the user has asked to send a message now.
    static final Duration SEND_NOW_CHECK_INTERVAL = Duration.ofSeconds(2);
    static final String CUT_SHORT_RESULT = "Not finished: the user sent a message to be read now, so this call was "
                                           + "given up on before it returned. Whatever it started (e.g. on its "
                                           + "server) may still be going on: check before running it again.";
    static final String SKIPPED_FOR_MESSAGE = "Not run: the user sent a message to be read first.";
    /**
     * Tool results shorter than this are never shortened.
     */
    static final int SHORTENED_MIN_CHARS = 300;
    /// How many times a call the model says is too large is made smaller and made again: one for each of
    /// [#shrink]'s steps, at most.
    static final int MAX_OVERFLOW_RETRIES = 4;
    /// Put in place of the arguments of an earlier tool call that have been shortened, with how they started.
    static final String SHORTENED_ARGUMENTS_KEY = "shortenedToSaveSpace";
    static final int SHORTENED_PREVIEW_CHARS = 200;
    /// The most characters of each part of an earlier step put in a note.
    static final int COLLAPSED_LINE_CHARS = 160;
    static final String COLLAPSED_STEPS_NOTE = "[Earlier steps of your answer, put briefly to save space in the "
                                               + "context window. Their calls and results are stored: call "
                                               + "read_turn (with no turn id, for this turn) to read them again if "
                                               + "you need them.]";
    /// How many replies cut off by the context window filling up the run carries on from in a row.
    static final int MAX_CUT_OFF_CONTINUATIONS = 2;
    static final String CUT_OFF_NOTE = "[Your last reply was cut off because the context window filled up. Earlier "
                                       + "steps have been shortened to make room. Carry on with the task from where "
                                       + "you were; if you were writing your answer, write it again in full, more "
                                       + "briefly.]";
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
    private final AiDao aiDao;
    private final AiChatRunManager runManager;
    private final WorkspaceStore workspaceStore;
    private final ContextUsageTracker usageTracker;
    private final ToolConsentGate consentGate;
    private final AiAuditLog auditLog;
    private final ModelStreamer modelStreamer;
    private final ToolGroups.Activations toolGroupActivations;
    private final Provider<AiConfig> aiConfigProvider;
    private final UserActions userActions;
    private final WorkspaceArguments workspaceArguments;
    private final CurrentToolGrants currentToolGrants;

    @Inject
    McpAgentChat(final AiService aiService,
                 final BuiltInTools builtInTools,
                 final AiChatRunManager runManager,
                 final WorkspaceStore workspaceStore,
                 final ContextUsageTracker usageTracker,
                 final ToolConsentGate consentGate,
                 final AiAuditLog auditLog,
                 final ModelStreamer modelStreamer,
                 final ToolGroups.Activations toolGroupActivations,
                 final Provider<AiConfig> aiConfigProvider,
                 final AiDao aiDao,
                 final UserActions userActions,
                 final WorkspaceArguments workspaceArguments,
                 final CurrentToolGrants currentToolGrants) {
        this.userActions = userActions;
        this.workspaceArguments = workspaceArguments;
        this.currentToolGrants = currentToolGrants;
        this.aiService = aiService;
        this.aiDao = aiDao;
        this.toolGroupActivations = toolGroupActivations;
        this.aiConfigProvider = aiConfigProvider;
        this.modelStreamer = modelStreamer;
        this.builtInTools = builtInTools;
        this.runManager = runManager;
        this.workspaceStore = workspaceStore;
        this.usageTracker = usageTracker;
        this.consentGate = consentGate;
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
        final ToolRunContext context = new ToolRunContext(chatModel, modelDoc, cancelled::get);
        return context.run(() -> chatWithTools(chatModel, modelDoc, chatId, messages, toolset, workingMessageId,
                cancelled, taskContext, attempt, debugLog, steerAfterMessageId, budget));
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
        // What this run shortens is counted afresh, as it starts from the stored chat.
        usageTracker.resetShortened(chatId);
        // Anything in the chat that came from tools or Stroom data could hold planted instructions.
        if (aiService.getMessages(chatId).stream()
                .anyMatch(m -> UNTRUSTED_MESSAGE_TYPES.contains(m.getMessageType()))) {
            runManager.markTainted(chatId);
        }
        // A call to an untrusted MCP server's tool earlier in the chat means its output may hold planted
        // instructions.
        if (aiService.getMessages(chatId).stream()
                .filter(m -> m.getMessageType() == AiMessageType.TOOL_CALL)
                .map(m -> read(m, ToolCallRecord.class))
                .filter(Objects::nonNull)
                .anyMatch(call -> isUntrustedServerTool(toolset, call.tool()))) {
            runManager.markReadUntrustedServer(chatId);
        }
        final Map<ToolSpecification, ToolExecutor> builtIns = new LinkedHashMap<>();
        final boolean toolsEnabled = modelDoc == null || modelDoc.isToolsEnabled();
        if (toolsEnabled) {
            builtIns.putAll(builtInTools.toolMap());
        }
        // In Ask and Plan modes, the model may only use the tools that cannot change anything.
        final boolean readOnlyMode = Objects.requireNonNullElse(aiDao.getAgentMode(chatId), AiAgentMode.AGENT)
                .isReadOnly();
        if (readOnlyMode) {
            builtIns.keySet().removeIf(specification -> !builtInTools.isReadOnly(specification.name()));
        }
        // What the user's tool policies grant: none of a category's tools unless it is granted, and with a
        // read-only grant only the tools (and calls) that cannot change anything.
        final ToolGrants grants = currentToolGrants.forCurrentUser();
        builtIns.keySet().removeIf(specification -> !builtInTools.category(specification.name())
                .map(category -> grants.offers(category, builtInTools.isReadOnly(specification.name()),
                        builtInTools.hasReadOnlyCalls(specification.name())))
                .orElse(true));
        Map<ToolSpecification, ToolExecutor> mcpTools = mcpTools(toolset, toolsEnabled, readOnlyMode, grants);
        // A large set of MCP tools is offered as groups the model turns on when it needs them.
        ToolGroups toolGroups = ToolGroups.of(mcpTools, toolset, toolGroupThreshold());
        Map<ToolSpecification, ToolExecutor> tools = offeredTools(chatId, builtIns, toolGroups, modelDoc);
        final List<ChatMessage> conversation = withUnavailableServersNote(messages, toolset.getWarnings());
        // Trusted servers' instructions on how to use their tools, for the servers whose tools are offered.
        // With the build tools' instructions (as the Stroom MCP server gave them to its clients), when they are
        // offered.
        final String serverInstructions = joined(toolset.serverInstructions(mcpTools.keySet().stream()
                        .map(specification -> toolset.toolInfo(specification.name()).map(McpToolInfo::serverKey))
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet())),
                builtInTools.buildInstructions(builtIns.keySet().stream()
                        .map(ToolSpecification::name)
                        .collect(Collectors.toSet())).orElse(null));
        withServerInstructions(conversation, serverInstructions);
        usageTracker.recordServerInstructions(chatId, serverInstructions);
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
        resumePending(chatId, conversation, tools, toolset, workingMessageId, taskContext, debugLog);


        final int maxIterations = maxToolIterations();
        // From the question's list so far, as one kept before the run stopped for the user's approval is still to be
        // kept up to date.
        final TaskListReminder taskListReminder = TaskListReminder.continuing(
                latestTaskList(aiService.getMessages(chatId)));
        ChatResponse response = null;
        int cutOffs = 0;
        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (cancelled.get() || taskContext.isTerminated()) {
                throw new TaskTerminatedException();
            }
            // Pick up anything the user has added since the last model call, and any group of tools the model has
            // turned on.
            if (iteration > 1) {
                conversation.addAll(steerFeed.poll());
                // A server that has said its tools changed while the run worked has them listed again, so the
                // model is offered what the server has now, not what it had when the run started.
                final List<String> changed = toolset.serversWithChangedTools();
                if (!changed.isEmpty()) {
                    LOGGER.debug(() -> "chat: chatId=" + chatId + " the tools of " + changed + " have changed");
                    mcpTools = mcpTools(toolset, toolsEnabled, readOnlyMode, grants);
                    toolGroups = ToolGroups.of(mcpTools, toolset, toolGroupThreshold());
                    noteToolsChanged(conversation, changed);
                }
                tools = offeredTools(chatId, builtIns, toolGroups, modelDoc);
            }
            final List<ToolSpecification> toolSpecifications = List.copyOf(tools.keySet());
            LOGGER.debug(() -> "chat: chatId=" + chatId + " offering " + toolSpecifications.size()
                               + " tool(s): " + toolSpecifications.stream()
                                       .map(ToolSpecification::name)
                                       .collect(Collectors.joining(", ")));

            // On the last step no tools are offered, so the model has to answer in words. It is told why, so
            // that it sums up rather than saying what it will do next.
            final List<ToolSpecification> offered;
            if (iteration < maxIterations) {
                offered = toolSpecifications;
            } else {
                offered = null;
                conversation.add(UserMessage.from(LAST_STEP_NOTE));
            }

            final int currentIteration = iteration;
            response = LOGGER.logDurationIfDebugEnabled(
                    () -> call(chatModel, conversation, offered, chatId, modelDoc, budget),
                    r -> "chat chatId=" + chatId + " iteration=" + currentIteration
                         + " toolCalls=" + r.aiMessage().toolExecutionRequests().size());

            final AiMessage aiMessage = response.aiMessage();
            if (!aiMessage.hasToolExecutionRequests()) {
                // A reply cut off as the context window filled up isn't the answer: carry on with more room.
                if (response.finishReason() == FinishReason.LENGTH && iteration < maxIterations
                    && cutOffs < MAX_CUT_OFF_CONTINUATIONS) {
                    cutOffs++;
                    LOGGER.info(() -> "The model's reply for chatId=" + chatId + " was cut off at the length limit; "
                                      + "making room and carrying on");
                    if (NullSafe.isNonBlankString(aiMessage.text())) {
                        conversation.add(AiMessage.from(aiMessage.text()));
                    }
                    conversation.add(UserMessage.from(CUT_OFF_NOTE));
                    usageTracker.recordShortened(chatId, shrink(conversation));
                    continue;
                }
                // If the user added something while the model worked, carry on with it.
                if (iteration < maxIterations && steerFeed.addTo(conversation, aiMessage)) {
                    continue;
                }
                if (iteration == maxIterations) {
                    noteStepLimit(chatId, maxIterations);
                }
                return response;
            }

            // Whatever the model said alongside its tool calls is its own explanation of what it is
            // trying to find out - the best "why" to show the user and to record.
            final String rationale = concise(aiMessage.text(), PROGRESS_MESSAGE_LIMIT);
            appendDebugRationale(debugLog, attempt, currentIteration, aiMessage.text());

            conversation.add(aiMessage);
            // Shown in its place, before anything the tools then ask the user (e.g. a form), rather than only
            // while it streams.
            if (NullSafe.isNonBlankString(aiMessage.text())) {
                aiService.storeMessage(chatId, AiMessageType.INTERIM_RESPONSE, aiMessage.text().strip());
            }
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
                final boolean unattended = consentGate.isUserAway(ToolSession.chat(chatId));
                // A tool that isn't offered (e.g. a name the model made up) can't run, so the user isn't asked
                // about it, and the model is told at once.
                if (!isOffered(tools, toolRequest.name())) {
                    conversation.add(ToolExecutionResultMessage.from(toolRequest,
                            recordNotRun(chatId, toolRequest, notOfferedReason(toolRequest.name(), toolGroups),
                                    unattended, "No such tool")));
                    continue;
                }
                // A call that changes something, of a tool the user's policies grant only for reading (e.g.
                // memory's write).
                final String policyRefusal = policyRefusal(grants, builtInTools, toolset, toolRequest);
                if (policyRefusal != null) {
                    conversation.add(ToolExecutionResultMessage.from(toolRequest,
                            recordNotRun(chatId, toolRequest, policyRefusal, unattended,
                                    "Not allowed by tool policy")));
                    continue;
                }
                // Turning on a group of tools changes nothing, so needs no approval.
                final ToolConsent.Verdict verdict = ToolGroups.isActivationTool(toolRequest.name())
                                                    && toolGroups.isGrouped()
                        ? ToolConsent.Verdict.RUN
                        : consentGate.checkCall(ToolSession.chat(chatId), toolRequest.name(),
                                toolRequest.arguments(), builtInTools, toolset, unattended);
                // The user has asked for their message to be read now, so the step's other calls wait for it.
                if (verdict.kind() == ToolConsent.Kind.RUN
                    && aiService.hasMessageOfType(chatId, AiMessageType.SEND_NOW_STEER_MESSAGE)) {
                    conversation.add(ToolExecutionResultMessage.from(toolRequest,
                            recordNotRun(chatId, toolRequest, SKIPPED_FOR_MESSAGE, unattended,
                                    "Skipped for the user's message")));
                    continue;
                }
                switch (verdict.kind()) {
                    case RUN -> {
                        final int callNo = t + 1;
                        final Map<ToolSpecification, ToolExecutor> toolsNow = tools;
                        conversation.add(ToolExecutionResultMessage.from(toolRequest,
                                runShowingProgress(toolset, toolRequest, workingMessageId, progress, () ->
                                        runAndRecord(chatId, callMessage, toolRequest, toolsNow, toolset, debugLog,
                                                callNo, unattended))));
                    }
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
            if (taskListReminder.afterStep(toolRequests)) {
                remindAboutTaskList(conversation);
            }
            if (!pending.isEmpty()) {
                // Stop here, giving up the thread and the slot, until the user answers on the card the chat shows.
                // The run carries on from the stored calls (see resumePending).
                final ApprovalOutcome outcome = consentGate.requestCallApproval(ToolSession.chat(chatId),
                        approvalRequest(pending, askVerdicts));
                if (!outcome.isPending()) {
                    // A chat's gate always asks on a card; only another session's could answer at once.
                    throw new IllegalStateException("A chat's tool approvals are answered on the chat's card");
                }
                throw new AiChatRunManager.WaitingForUserException(pending.size() + " tool call(s) in chat "
                                                                   + chatId + " need approval");
            }
        }

        // Loop cap hit. Make one final call with no tools so the model has to answer in words.
        noteStepLimit(chatId, maxIterations);
        if (response != null && response.aiMessage().hasToolExecutionRequests()) {
            return call(chatModel, conversation, null, chatId, modelDoc, budget);
        }
        return response;
    }

    /// Runs a tool call, showing on the working line what it has got to while it runs, as its MCP server reports
    /// it, e.g. "Surveying the index (describe_document) — Reading documents (2 of 5)…".
    ///
    /// @param working What the working line says of the call.
    /// @param call    Runs the call.
    /// @return What the call returns.
    private String runShowingProgress(final McpToolset toolset,
                                      final ToolExecutionRequest toolRequest,
                                      final int workingMessageId,
                                      final String working,
                                      final Supplier<String> call) {
        if (toolRequest.id() == null) {
            return call.get();
        }
        final AtomicReference<String> shown = new AtomicReference<>();
        toolset.listenForProgress(toolRequest.id(), update -> {
            final String text = working + " — " + truncate(update, PROGRESS_UPDATE_LIMIT) + "…";
            // A server may report the same thing many times; the line is only changed when what it says changes.
            if (!text.equals(shown.getAndSet(text))) {
                aiService.updateMessageText(workingMessageId, text);
            }
        });
        try {
            return call.get();
        } finally {
            toolset.stopListening(toolRequest.id());
        }
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
                                final McpToolset toolset,
                                final StringBuilder debugLog,
                                final int callNo,
                                final boolean unattended) {
        if (builtInTools.readsUntrustedContent(toolRequest.name())) {
            runManager.markTainted(chatId);
        }
        if (isUntrustedServerTool(toolset, toolRequest.name())) {
            runManager.markReadUntrustedServer(chatId);
        }
        final long startMs = System.currentTimeMillis();
        // An MCP tool's arguments may name workspace files, whose content is put in before the call is sent, so the
        // model need not write a large argument out. What the model wrote is what is stored and audited.
        final WorkspaceArguments.Expansion expansion = isBuiltIn(toolRequest.name())
                ? WorkspaceArguments.Expansion.unchanged(toolRequest.arguments())
                : workspaceArguments.expand(chatId, toolRequest.arguments());
        if (expansion.isExpanded()) {
            // As for any other read of the files.
            runManager.markTainted(chatId);
        }
        final ToolResult toolResult = expansion.error() != null
                ? ToolResult.text("Error: " + expansion.error())
                : execute(tools, withArguments(toolRequest, expansion), toolRequest.name(), chatId,
                        !isBuiltIn(toolRequest.name()));
        final String result = toolResult.text();
        final long durationMs = System.currentTimeMillis() - startMs;
        runManager.addUsage(chatId, 0, 0, 1);
        LOGGER.trace(() -> "Tool '" + toolRequest.name() + "' result:\n" + result);
        appendDebugToolCall(debugLog, callNo, toolRequest, result);

        // A large result is saved to the workspace and the model is given a stub, so it does not fill the
        // model's context. A result the user must be shown exactly as it is (e.g. a needs_review reply) is
        // given in full up to a higher limit, as the model can't show the user a stub (see ToolOutputs).
        final boolean large = ToolOutputs.isLarge(result) && !builtInTools.boundsItsOutput(toolRequest.name());
        final String saved = large
                ? ToolOutputs.forSaving(result)
                : result;
        final String path = large
                ? saveOutput(chatId, callMessage, saved)
                : null;
        final String forModel = path != null
                ? ToolOutputs.stub(saved, path)
                : result;
        final boolean error = isError(result);
        // Steps that are the user's, checked, to show as buttons. The model is not told about them: the steps are
        // in the result's text too.
        // Stroom's own tools offer theirs with their result.
        final List<Map<String, Object>> builtInActions = toolResult.userActions().stream()
                .map(ToolUserAction::toMap)
                .toList();
        final List<UserActionRecord> actions = userActions.check(toolset.takeUserActions(toolRequest.id()),
                builtInActions, System.currentTimeMillis());
        aiService.storeMessage(chatId, AiMessageType.TOOL_RESULT,
                JsonUtil.writeValueAsString(new ToolResultRecord(toolRequest.id(), toolRequest.name(),
                        error, forModel, path, result.length(), durationMs, actions.isEmpty()
                        ? null
                        : actions), false));
        // The arguments as the model wrote them, with the size of those sent if workspace files were put in.
        final Long expandedArgumentChars = expansion.isExpanded()
                ? (long) expansion.arguments().length()
                : null;
        auditLog.toolCall(ToolSession.chat(chatId), toolRequest.name(), toolRequest.arguments(),
                expandedArgumentChars, error
                        ? "Failed"
                        : "Run", !error, unattended);
        return forModel;
    }

    /// @return The call as it is sent: with the workspace files its arguments name put in, if there were any.
    private static ToolExecutionRequest withArguments(final ToolExecutionRequest toolRequest,
                                                      final WorkspaceArguments.Expansion expansion) {
        if (!expansion.isExpanded()) {
            return toolRequest;
        }
        return ToolExecutionRequest.builder()
                .id(toolRequest.id())
                .name(toolRequest.name())
                .arguments(expansion.arguments())
                .build();
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
                        null, text.length(), 0, null), false));
        auditLog.toolCall(ToolSession.chat(chatId), toolRequest.name(), toolRequest.arguments(), outcome, false,
                unattended);
        return text;
    }

    /**
     * Finishes the tool calls of the current turn that were waiting for the user's approval when the run last
     * stopped: those allowed are run, and the model is told about the rest.
     */
    private void resumePending(final int chatId,
                               final List<ChatMessage> conversation,
                               final Map<ToolSpecification, ToolExecutor> tools,
                               final McpToolset toolset,
                               final int workingMessageId,
                               final TaskContext taskContext,
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
        // The user's tool policies may have changed since the calls were made.
        final ToolGrants grants = currentToolGrants.forCurrentUser();
        final Set<String> answered = new HashSet<>();
        // Calls that asked the user questions and stopped the run to wait for the answers (ask_user).
        final Set<String> askedTheUser = new HashSet<>();
        ToolApprovalRequest approval = null;
        for (final AiChatMessage message : turn) {
            if (message.getMessageType() == AiMessageType.TOOL_RESULT) {
                NullSafe.consume(read(message, ToolResultRecord.class), result -> answered.add(result.callId()));
            } else if (message.getMessageType() == AiMessageType.ELICITATION) {
                // A call may ask several forms, each with an id of its own made from the call's.
                NullSafe.consume(read(message, ElicitationRecord.class), record ->
                        NullSafe.consume(record.callId(), id -> askedTheUser.add(UserQuestions.toolCallId(id))));
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
            final boolean unattended = consentGate.isUserAway(ToolSession.chat(chatId));
            final String result;
            final String policyRefusal = policyRefusal(grants, builtInTools, toolset, request);
            // A call that asked the user questions is made again, and given their answer (or stops the run again if
            // they still haven't answered).
            if (policyRefusal != null) {
                result = recordNotRun(chatId, request, policyRefusal, unattended, "Not allowed by tool policy");
            } else if (askedTheUser.contains(call.callId())
                || (decided != null && decided.decision() == Decision.ALLOWED)) {
                LOGGER.debug(() -> "Running tool '" + call.tool() + "' as the user has answered, in chatId="
                                   + chatId);
                // Said on the working line as when the call was made, with what it gets to as it runs.
                final String progress = toolCallProgressMessage(call.rationale(), request);
                taskContext.info(() -> progress);
                aiService.updateMessageText(workingMessageId, progress + "…");
                final int thisCallNo = ++callNo;
                result = runShowingProgress(toolset, request, workingMessageId, progress, () ->
                        runAndRecord(chatId, message, request, tools, toolset, debugLog, thisCallNo, unattended));
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
                null,
                NullSafe.get(info, McpToolInfo::briefDescription));
    }

    /// @param message A message the user sent to steer a run.
    /// @return What the model is given of it: the message, saying it was sent while the model worked and, if the tab
    /// the user attached was sent with it, that the tab may have changed.
    static String steerText(final AiChatMessage message) {
        return STEER_PREFIX + message.getMessage() + (message.getAttachmentId() != null
                ? "\n\n" + STEER_TAB_NOTE
                : "");
    }

    static ToolApprovalRequest approvalRequest(final List<PendingCall> pending,
                                               final List<ToolConsent.Verdict> verdicts) {
        // Only what the card's classifications and trust icons don't already say.
        final Set<String> reasons = verdicts.stream()
                .map(ToolConsent.Verdict::cardReason)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ToolApprovalRequest(
                ToolApprovalRequest.KIND,
                ToolApprovalRequest.Status.PENDING,
                reasons.isEmpty()
                        ? null
                        : String.join(" ", reasons),
                pending,
                verdicts.stream().allMatch(ToolConsent.Verdict::allowForChat),
                verdicts.stream().allMatch(ToolConsent.Verdict::allowServerReadOnly));
    }

    /// @return True if the tool is an untrusted MCP server's, or one of the resource tools while any server is
    /// untrusted (as they read from all of them).
    private boolean isUntrustedServerTool(final McpToolset toolset, final String toolName) {
        if (toolset == null || toolName == null || isBuiltIn(toolName)) {
            return false;
        }
        return toolset.toolInfo(toolName)
                .map(info -> !info.trusted())
                .orElseGet(() -> toolset.hasUntrustedServer()
                                 && (McpToolset.LIST_RESOURCES_TOOL.equals(toolName)
                                     || McpToolset.GET_RESOURCE_TOOL.equals(toolName)));
    }

    private boolean isBuiltIn(final String toolName) {
        return builtInTools.toolMap().keySet().stream().anyMatch(spec -> spec.name().equals(toolName));
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
     * model's context window, it is made smaller first, a step at a time (see [#shrink]), until it fits: everything
     * is stored, and the model can read it again with read_turn. If the model says the request is too large
     * anyway, the chat's estimates are corrected from what it said, the conversation is made smaller again, and
     * the call is made again, so the run carries on where it was rather than starting again.
     *
     * @param tools The tools to offer, or null for none.
     * @throws RuntimeException What the model threw, if the conversation can't be made small enough.
     */
    private ChatResponse call(final ChatModel chatModel,
                              final List<ChatMessage> conversation,
                              final List<ToolSpecification> tools,
                              final int chatId,
                              final OpenAIModelDoc modelDoc,
                              final ContextBudget budget) {
        fitToBudget(conversation, tools, chatId, budget);
        for (int retry = 1; ; retry++) {
            try {
                return callOnce(chatModel, conversation, tools, chatId, modelDoc);
            } catch (final RuntimeException e) {
                if (!ChatCompactor.isContextOverflowError(e) || retry > MAX_OVERFLOW_RETRIES) {
                    throw e;
                }
                final OptionalLong size = usageTracker.recordOverflow(chatId, conversation, tools, e,
                        NullSafe.getOrElse(modelDoc, OpenAIModelDoc::getMaxContextWindowTokens, 0));
                // At least one more step, as the estimate was wrong, then as many as the corrected estimate needs.
                final int shortened = shrink(conversation);
                if (shortened == 0) {
                    throw e;
                }
                usageTracker.recordShortened(chatId, shortened);
                fitToBudget(conversation, tools, chatId, budget);
                LOGGER.info(() -> "The request for chatId=" + chatId + " was too large for the context window ("
                                  + (size.isPresent()
                        ? size.getAsLong() + " tokens"
                        : "size not given") + "); made the conversation smaller and trying again");
            }
        }
    }

    /// Makes the conversation smaller, a step at a time, until the chat's estimate of it is within the budget, or
    /// there is nothing more to shorten.
    private void fitToBudget(final List<ChatMessage> conversation,
                             final List<ToolSpecification> tools,
                             final int chatId,
                             final ContextBudget budget) {
        int shortened = 0;
        while (budget.isOver(usageTracker.estimateCautiously(chatId, conversation, tools))) {
            final int step = shrink(conversation);
            if (step == 0) {
                break;
            }
            shortened += step;
        }
        usageTracker.recordShortened(chatId, shortened);
        if (shortened > 0) {
            final int total = shortened;
            LOGGER.debug(() -> "Shortened " + total + " part(s) of chatId=" + chatId
                               + "'s conversation to keep within the context window");
        }
    }

    /// Takes the first step that makes the conversation smaller, least lossy first:
    ///
    /// 1. earlier tool results, keeping the latest [#RECENT_RESULTS_KEPT];
    /// 2. the arguments of earlier steps' tool calls (e.g. whole code proposed), keeping the latest step's;
    /// 3. earlier tool results, keeping only the latest;
    /// 4. earlier steps, each put as a line or two in a note.
    ///
    /// The latest step, and its latest result, are always kept, so the model can carry on from it.
    ///
    /// @param conversation The conversation, changed in place.
    /// @return How many parts were shortened, or 0 if there was nothing more to shorten.
    static int shrink(final List<ChatMessage> conversation) {
        int shortened = shortenToolResults(conversation, RECENT_RESULTS_KEPT);
        if (shortened == 0) {
            shortened = shortenToolCallArguments(conversation, 1);
        }
        if (shortened == 0) {
            shortened = shortenToolResults(conversation, 1);
        }
        if (shortened == 0) {
            shortened = collapseEarlierSteps(conversation, 1);
        }
        return shortened;
    }

    /// Replaces long arguments of earlier steps' tool calls with a short note of how they started. The arguments
    /// stay valid JSON, as some model servers read them.
    ///
    /// @param conversation The conversation, changed in place.
    /// @param keepSteps    How many of the latest steps to leave as they are.
    /// @return How many calls' arguments were shortened.
    static int shortenToolCallArguments(final List<ChatMessage> conversation, final int keepSteps) {
        final List<Integer> steps = stepIndexes(conversation);
        int shortened = 0;
        for (int i = 0; i < steps.size() - keepSteps; i++) {
            final int index = steps.get(i);
            final AiMessage step = (AiMessage) conversation.get(index);
            boolean changed = false;
            final List<ToolExecutionRequest> requests = new ArrayList<>();
            for (final ToolExecutionRequest request : step.toolExecutionRequests()) {
                final String arguments = NullSafe.string(request.arguments());
                if (arguments.length() > SHORTENED_MIN_CHARS && !arguments.contains(SHORTENED_ARGUMENTS_KEY)) {
                    requests.add(request.toBuilder()
                            .arguments(JsonUtil.writeValueAsString(Map.of(SHORTENED_ARGUMENTS_KEY,
                                    truncate(arguments, SHORTENED_PREVIEW_CHARS)), false))
                            .build());
                    changed = true;
                    shortened++;
                } else {
                    requests.add(request);
                }
            }
            if (changed) {
                conversation.set(index, AiMessage.builder()
                        .text(step.text())
                        .toolExecutionRequests(requests)
                        .build());
            }
        }
        return shortened;
    }

    /// Puts earlier steps (the model's tool calls and their results) as a line or two each in a note, in their
    /// place. Steps next to each other share a note.
    ///
    /// @param conversation The conversation, changed in place.
    /// @param keepSteps    How many of the latest steps to leave as they are.
    /// @return How many steps were put in notes.
    static int collapseEarlierSteps(final List<ChatMessage> conversation, final int keepSteps) {
        final List<Integer> steps = stepIndexes(conversation);
        if (steps.size() <= keepSteps) {
            return 0;
        }
        final Set<Integer> collapse = new HashSet<>(steps.subList(0, steps.size() - keepSteps));
        final List<ChatMessage> result = new ArrayList<>();
        StringBuilder note = null;
        int collapsed = 0;
        int i = 0;
        while (i < conversation.size()) {
            final ChatMessage message = conversation.get(i);
            if (!collapse.contains(i)) {
                if (note != null) {
                    result.add(UserMessage.from(note.toString().stripTrailing()));
                    note = null;
                }
                result.add(message);
                i++;
                continue;
            }
            if (note == null) {
                note = new StringBuilder(COLLAPSED_STEPS_NOTE).append('\n');
            }
            final AiMessage step = (AiMessage) message;
            if (NullSafe.isNonBlankString(step.text())) {
                note.append("- You said: ").append(oneLine(step.text(), COLLAPSED_LINE_CHARS)).append('\n');
            }
            final Map<String, String> results = new HashMap<>();
            i++;
            while (i < conversation.size() && conversation.get(i) instanceof final ToolExecutionResultMessage r) {
                results.put(r.id(), r.text());
                i++;
            }
            for (final ToolExecutionRequest request : step.toolExecutionRequests()) {
                note.append("- ").append(request.name()).append('(')
                        .append(oneLine(request.arguments(), COLLAPSED_LINE_CHARS)).append(") → ")
                        .append(oneLine(results.getOrDefault(request.id(), "no result"), COLLAPSED_LINE_CHARS))
                        .append('\n');
            }
            collapsed++;
        }
        if (note != null) {
            result.add(UserMessage.from(note.toString().stripTrailing()));
        }
        conversation.clear();
        conversation.addAll(result);
        return collapsed;
    }

    /// @return Where the model's steps (its messages with tool calls) are in the conversation, in order.
    private static List<Integer> stepIndexes(final List<ChatMessage> conversation) {
        final List<Integer> steps = new ArrayList<>();
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i) instanceof final AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                steps.add(i);
            }
        }
        return steps;
    }

    private static String oneLine(final String text, final int maxChars) {
        return truncate(NullSafe.string(text).replaceAll("\\s+", " ").strip(), maxChars);
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
    /// @return The MCP tools to offer, by the names they are offered as: none if the model has tools turned off,
    /// and only those that cannot change anything in a read-only mode.
    private Map<ToolSpecification, ToolExecutor> mcpTools(final McpToolset toolset,
                                                          final boolean toolsEnabled,
                                                          final boolean readOnlyMode,
                                                          final ToolGrants grants) {
        final Map<ToolSpecification, ToolExecutor> mcpTools = new LinkedHashMap<>();
        if (toolsEnabled) {
            // Named so as not to clash with any of the built-in tools, whether or not they are offered.
            addMcpTools(mcpTools, toolset, builtInTools.toolMap().keySet());
        }
        if (readOnlyMode) {
            mcpTools.keySet().removeIf(specification -> !toolset.isReadOnly(specification.name()));
        }
        mcpTools.keySet().removeIf(specification -> {
            final String name = specification.name();
            return !grants.offers(toolset.category(name), toolset.isReadOnly(name), toolset.isReadOnly(name));
        });
        return mcpTools;
    }

    /// @return Why the user's tool policies don't allow the call, or null if they do (or it isn't one of Stroom's
    /// tools or a server's, e.g. a tool that turns on a group).
    static String policyRefusal(final ToolGrants grants,
                                final BuiltInTools builtInTools,
                                final McpToolset toolset,
                                final ToolExecutionRequest toolRequest) {
        final String name = toolRequest.name();
        final AiToolCategory category;
        final boolean readOnlyCall;
        final Optional<AiToolCategory> builtInCategory = builtInTools.category(name);
        if (builtInCategory.isPresent()) {
            category = builtInCategory.get();
            readOnlyCall = builtInTools.isReadOnlyCall(name, toolRequest.arguments());
        } else if (toolset != null && toolset.toolInfo(name).isPresent()) {
            category = toolset.category(name);
            readOnlyCall = toolset.isReadOnly(name);
        } else {
            return null;
        }
        if (grants.allowsCall(category, readOnlyCall)) {
            return null;
        }
        return grants.access(category).isPresent()
                ? "Not run: the user's tool policies allow only the " + category.getDisplayValue() + " tools and "
                  + "calls that cannot change anything, and this call would change something. Tell the user, who "
                  + "can ask an administrator for a policy that grants read and write."
                : "Not run: the user's tool policies don't allow " + category.getDisplayValue() + " tools.";
    }

    /// Tells the model, with the last tool result, that servers' tools have changed, so it calls them as they are
    /// now offered, rather than as it saw them earlier in the run.
    static void noteToolsChanged(final List<ChatMessage> conversation, final List<String> servers) {
        final int last = conversation.size() - 1;
        if (last >= 0 && conversation.get(last) instanceof final ToolExecutionResultMessage result) {
            conversation.set(last, ToolExecutionResultMessage.from(result.id(), result.toolName(),
                    result.text() + "\n\nThe tools of MCP server(s) " + String.join(", ", servers)
                    + " have changed: call them only as they are offered now, as their names, arguments or what "
                    + "they do may differ from before."));
        }
    }

    private void addMcpTools(final Map<ToolSpecification, ToolExecutor> tools,
                             final McpToolset toolset,
                             final Set<ToolSpecification> builtIns) {
        if (toolset.isEmpty()) {
            return;
        }
        final Set<String> names = builtIns.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        // Named so as not to clash with the built-in tools.
        toolset.tools(Set.copyOf(names)).forEach((specification, executor) -> {
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

    /// @param messages A chat's messages, in order.
    /// @return The latest task list kept for the chat's latest question, or null if it has none (a list kept for an
    /// earlier question isn't the one the user sees).
    static String latestTaskList(final List<AiChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            final AiChatMessage message = messages.get(i);
            if (message.getMessageType() == AiMessageType.TASK_LIST) {
                return message.getMessage();
            }
            if (message.getMessageType() == AiMessageType.USER_MESSAGE) {
                return null;
            }
        }
        return null;
    }

    /// Adds the task list reminder to the last tool result, so the model sees it before its next step.
    private static void remindAboutTaskList(final List<ChatMessage> conversation) {
        final int last = conversation.size() - 1;
        if (last >= 0 && conversation.get(last) instanceof final ToolExecutionResultMessage result) {
            conversation.set(last, ToolExecutionResultMessage.from(result.id(), result.toolName(),
                    result.text() + "\n\n" + TaskListReminder.REMINDER));
        }
    }

    private static boolean isOffered(final Map<ToolSpecification, ToolExecutor> tools, final String toolName) {
        return tools.keySet().stream().anyMatch(specification -> specification.name().equals(toolName));
    }

    /// @return What the model is told when it calls a tool it isn't offered: how to turn the tool on, if it is
    /// in a group that is off.
    static String notOfferedReason(final String toolName, final ToolGroups toolGroups) {
        return toolGroups.groupOf(toolName)
                .map(group -> "Error: '" + toolName + "' is one of the '" + group + "' tools, which aren't on. "
                              + "Call " + ToolGroups.ACTIVATE_PREFIX + group + " first.")
                .orElseGet(() -> "Error: no such tool '" + toolName + "'. Call only the tools you are offered.");
    }

    /**
     * Tells the user the run stopped at the step limit, and how to carry on.
     */
    private void noteStepLimit(final int chatId, final int maxIterations) {
        LOGGER.info("chat: hit the tool iteration cap of {} for chatId={}", maxIterations, chatId);
        aiService.storeMessage(chatId, AiMessageType.WARNING, "Stopped after " + maxIterations
                                                              + " steps, the most for one question. Reply "
                                                              + "'continue' to carry on.");
    }

    private int maxToolIterations() {
        return Math.max(1, NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMaxToolIterations,
                AiConfig.DEFAULT_MAX_TOOL_ITERATIONS));
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

    /**
     * Adds MCP servers' instructions to the end of the system prompt, if there are any.
     *
     * @param conversation The conversation, which starts with the system prompt. It is changed in place.
     * @param instructions The instructions, or null.
     */
    // Instructions one after the other, leaving out those that are blank; null if all are.
    private static String joined(final String... instructions) {
        final String text = Arrays.stream(instructions)
                .filter(NullSafe::isNonBlankString)
                .collect(Collectors.joining("\n\n"));
        return text.isEmpty()
                ? null
                : text;
    }

    static void withServerInstructions(final List<ChatMessage> conversation, final String instructions) {
        if (NullSafe.isBlankString(instructions)) {
            return;
        }
        if (!conversation.isEmpty() && conversation.getFirst() instanceof final SystemMessage systemMessage) {
            conversation.set(0, SystemMessage.from(systemMessage.text() + "\n\n" + instructions));
        } else {
            conversation.addFirst(SystemMessage.from(instructions));
        }
    }

    /// @param stoppable True if the tool is to be given up on as soon as the run is stopped, rather than when it
    ///                  returns: an MCP server's, which can take as long as the server likes (e.g. while the user
    ///                  fills in its form). Built-in tools rely on the run's thread, and limit their own time.
    private ToolResult execute(final Map<ToolSpecification, ToolExecutor> tools,
                               final ToolExecutionRequest toolRequest,
                               final String toolName,
                               final int chatId,
                               final boolean stoppable) {
        final ToolExecutor executor = tools.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(toolName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (executor == null) {
            return ToolResult.text("Error: no such tool '" + toolName + "'");
        }
        // Stroom's own tools give a typed result (with any user actions they offer); others give only text.
        final Supplier<ToolResult> call = executor instanceof final StroomToolExecutor stroomTool
                ? () -> stroomTool.result(toolRequest, chatId)
                : () -> ToolResult.text(executor.execute(toolRequest, chatId));
        final AtomicBoolean stop = stoppable
                ? runManager.stopFlag(chatId)
                : null;
        try {
            return stop != null
                    ? StoppableCall.run(call, stop, sendNowCheck(chatId), STOP_CHECK_INTERVAL)
                    : call.get();
        } catch (final StoppableCall.CutShortException e) {
            LOGGER.debug(() -> "Tool '" + toolName + "' given up on for chatId=" + chatId
                               + " as the user sent a message to be read now");
            return ToolResult.text(CUT_SHORT_RESULT);
        } catch (final StoppableCall.StoppedException e) {
            LOGGER.debug(() -> "Tool '" + toolName + "' given up on for chatId=" + chatId + " as the run was stopped");
            return ToolResult.text("Error calling tool '" + toolName + "': the user stopped the chat before it "
                                   + "finished (its server may still finish what it started).");
        } catch (final AiChatRunManager.WaitingForUserException e) {
            // The run stops until the user answers (e.g. ask_user's questions), and makes the call again then.
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Tool '" + toolName + "' failed for chatId=" + chatId, e);
            return ToolResult.text("Error calling tool '" + toolName + "': "
                                   + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    /// @return Whether the user has asked to send a message now, looked up at most every
    /// [#SEND_NOW_CHECK_INTERVAL], as it is asked often while a tool call runs. The request is held in the
    /// database, so it is seen whichever node the user asked on.
    private BooleanSupplier sendNowCheck(final int chatId) {
        final AtomicLong nextCheckMs = new AtomicLong(System.currentTimeMillis() + SEND_NOW_CHECK_INTERVAL.toMillis());
        return () -> {
            final long nowMs = System.currentTimeMillis();
            if (nowMs < nextCheckMs.get()) {
                return false;
            }
            nextCheckMs.set(nowMs + SEND_NOW_CHECK_INTERVAL.toMillis());
            return aiService.hasMessageOfType(chatId, AiMessageType.SEND_NOW_STEER_MESSAGE);
        };
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
        final String firstLine = firstPlainLine(text);
        if (firstLine == null) {
            return null;
        }
        String t = firstLine.replaceAll("\\s+", " ");
        final int firstSentenceEnd = t.indexOf(". ");
        if (firstSentenceEnd > 0 && firstSentenceEnd < limit) {
            t = t.substring(0, firstSentenceEnd + 1);
        }
        return truncate(t, limit);
    }

    /// @param text Markdown, e.g. what the model wrote alongside its tool calls.
    /// @return Its first line with text in it, as plain text (without heading, list or quote marks, emphasis,
    /// code marks or link targets), e.g. "Records to translate" from `**Records to translate**`, or null if it
    /// has none.
    static String firstPlainLine(final String text) {
        if (text == null) {
            return null;
        }
        for (final String line : text.split("\\R")) {
            final String plain = line
                    // Heading, quote and list marks at the start.
                    .replaceAll("^\\s*(?:#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+[.)]\\s+)+", "")
                    // Images and links: just their text.
                    .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                    // Bold, italics, strike-through and code marks.
                    .replaceAll("(\\*\\*|__|~~|`)", "")
                    .replaceAll("(?<![\\w*])[*_](\\S(?:.*?\\S)?)[*_](?![\\w*])", "$1")
                    .strip();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return null;
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
                // A queued message is picked up by changing it to a steering message, so it is picked up once,
                // and shown to the user as sent.
                if (message.getMessageType() == AiMessageType.STEER_MESSAGE
                    || (message.getMessageType().isQueuedSteer()
                        && aiService.changeMessageType(chatId, message.getId(), AiChatRunManager.QUEUED_STEERS,
                        AiMessageType.STEER_MESSAGE))) {
                    LOGGER.debug(() -> "Picked up a steering message for chatId=" + chatId);
                    steers.add(UserMessage.from(steerText(message)));
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
