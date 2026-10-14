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
import stroom.ai.api.TableSource;
import stroom.ai.api.TableSummaryProgressListener;
import stroom.ai.api.TableSummaryRequest;
import stroom.ai.impl.ChatCompactor.CompactionMode;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.memory.MemoryChange.ApprovalStatus;
import stroom.ai.impl.workspace.TableProfiler;
import stroom.ai.impl.workspace.WorkspaceFileUnavailableException;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAttachmentDataPage;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiChatStatusResponse;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryListResponse;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolApprovalsResponse;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.DeleteAiMemoryRequest;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.SaveAiMemoryRequest;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.ai.shared.UndoAiMemoryChangeRequest;
import stroom.config.global.api.GlobalConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.OffsetRange;
import stroom.query.api.SearchRequestSource;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Answers "Ask Stroom AI" questions. Each question is answered by an agent run (see {@link AiChatRunManager}),
 * which is stopped through the run manager.
 */
@Singleton
public class AskStroomAIService {

    static final int MAX_CHAT_STATUSES = 100;
    static final String NOTHING_TO_COMPACT = "There is nothing to compact yet: the latest turns are always kept "
                                             + "in full.";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAIService.class);

    /**
     * Returned by processQuestion when it is stopped before any answer was produced. Not stored as an
     * answer, as the run's status says that it was stopped.
     */
    static final String CANCELLED_MESSAGE = "Analysis was cancelled before a response was produced.";

    /**
     * How long an edit waits for the chat's run to stop.
     */
    static final Duration STOP_WAIT = Duration.ofSeconds(30);
    private static final Duration STOP_POLL_INTERVAL = Duration.ofMillis(200);
    // Messages that are updated in place while a question is worked on, so are reported as they stand by every
    // poll rather than as new messages.
    private static final Set<AiMessageType> TRANSIENT_MESSAGE_TYPES = EnumSet.of(AiMessageType.WORKING,
            AiMessageType.STREAMING_ANSWER, AiMessageType.STREAMING_THINKING);

    /**
     * Polling interval (ms) when waiting for attachments.
     */
    private static final long ATTACHMENT_POLL_INTERVAL_MS = 1_000;

    private final AiService aiService;
    private final AiAttachmentFileStore attachmentFileStore;
    private final DashboardService dashboardService;
    private final QueryService queryService;
    private final OpenAIModelStore openAIModelStore;
    private final ResourceStore resourceStore;
    private final ExecutorProvider executorProvider;
    private final Provider<AskStroomAiConfig> defaultConfigProvider;
    private final Provider<GlobalConfig> globalConfigProvider;
    private final Provider<TableAnalysisConfig> tableAnalysisConfigProvider;
    private final TaskContextFactory taskContextFactory;
    private final TableSummariserImpl tableSummariser;
    private final McpToolServerFactory mcpToolServerFactory;
    private final McpClientCache mcpClientCache;
    private final AiChatRunManager runManager;
    private final AiChatRunDao runDao;
    private final WorkspaceStore workspaceStore;
    private final AiWorkspaceDao workspaceDao;
    private final ContextAssembler contextAssembler;
    private final ContextUsageTracker usageTracker;
    private final ChatCompactor chatCompactor;
    private final AiMemoryService memoryService;
    private final ToolApprovalService toolApprovalService;
    private final AiAuditLog auditLog;
    private final AiDao aiDao;
    private final Provider<AiConfig> aiConfigProvider;
    private final SecurityContext securityContext;
    private Duration stopWait = STOP_WAIT;
    private final McpAgentChat mcpAgentChat;

    @Inject
    public AskStroomAIService(final AiService aiService,
                              final AiAttachmentFileStore attachmentFileStore,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore,
                              final ResourceStore resourceStore,
                              final ExecutorProvider executorProvider,
                              final Provider<AskStroomAiConfig> defaultConfigProvider,
                              final Provider<GlobalConfig> globalConfigProvider,
                              final Provider<TableAnalysisConfig> tableAnalysisConfigProvider,
                              final TaskContextFactory taskContextFactory,
                              final TableSummariserImpl tableSummariser,
                              final McpToolServerFactory mcpToolServerFactory,
                              final McpClientCache mcpClientCache,
                              final McpAgentChat mcpAgentChat,
                              final AiChatRunManager runManager,
                              final AiChatRunDao runDao,
                              final WorkspaceStore workspaceStore,
                              final AiWorkspaceDao workspaceDao,
                              final ContextAssembler contextAssembler,
                              final ContextUsageTracker usageTracker,
                              final ChatCompactor chatCompactor,
                              final AiMemoryService memoryService,
                              final ToolApprovalService toolApprovalService,
                              final AiAuditLog auditLog,
                              final AiDao aiDao,
                              final Provider<AiConfig> aiConfigProvider,
                              final SecurityContext securityContext) {
        this.aiService = aiService;
        this.attachmentFileStore = attachmentFileStore;
        this.dashboardService = dashboardService;
        this.queryService = queryService;
        this.openAIModelStore = openAIModelStore;
        this.resourceStore = resourceStore;
        this.executorProvider = executorProvider;
        this.defaultConfigProvider = defaultConfigProvider;
        this.globalConfigProvider = globalConfigProvider;
        this.tableAnalysisConfigProvider = tableAnalysisConfigProvider;
        this.taskContextFactory = taskContextFactory;
        this.tableSummariser = tableSummariser;
        this.mcpToolServerFactory = mcpToolServerFactory;
        this.mcpClientCache = mcpClientCache;
        this.runManager = runManager;
        this.runDao = runDao;
        this.workspaceStore = workspaceStore;
        this.workspaceDao = workspaceDao;
        this.contextAssembler = contextAssembler;
        this.usageTracker = usageTracker;
        this.chatCompactor = chatCompactor;
        this.memoryService = memoryService;
        this.toolApprovalService = toolApprovalService;
        this.auditLog = auditLog;
        this.aiDao = aiDao;
        this.aiConfigProvider = aiConfigProvider;
        this.securityContext = securityContext;
        this.mcpAgentChat = mcpAgentChat;
    }

    /**
     * Main entry point for AI requests. Operates in two stages:
     * <ol>
     *   <li>If a context (table data source) is present, create an attachment and start downloading.</li>
     *   <li>If a message is present, process the question (waiting for any in-flight attachments).</li>
     * </ol>
     */
    public AskStroomAiResponse askStroomAi(final AskStroomAiRequest request) {
        final AiChat aiChat = request.getAiChat();
        final Integer chatId = NullSafe.get(aiChat, AiChat::getId);
        aiService.verifyOwnership(chatId);

        LOGGER.debug(() -> "askStroomAi: chatId=" + chatId
                           + " hasContext=" + (request.getContext() != null)
                           + " hasMessage=" + (request.getMessage() != null));

        // Stage 1: If context is present, create an attachment (and start async download if needed).
        if (request.getContext() != null) {
            try {
                createAttachment(chatId, request.getContext(), request.getConfig());
                LOGGER.debug(() -> "Attachment created for chatId=" + chatId
                                   + " contextType=" + request.getContext().getClass().getSimpleName());
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                aiService.storeMessage(chatId, AiMessageType.ERROR, e.getMessage());
            }
        }

        // Stage 2: If a user message is present, start a run to answer it in the background. The client
        // follows its progress by polling, so the request returns straight away. A message sent while a run is
        // working steers that run instead.
        if (request.getMessage() != null && !steer(chatId, request.getMessage())) {
            runManager.start(chatId, run -> {
                aiService.storeMessage(chatId, AiMessageType.USER_MESSAGE, request.getMessage());
                answerQuestion(chatId, request);
            });
        }

        // The answer (if there is a question) is delivered by polling.
        return new AskStroomAiResponse(null);
    }

    /**
     * Edits one of the user's earlier questions, or asks it again unchanged. The question and everything after
     * it are removed from the chat and the new text is answered in a new run. A run that is working on the chat
     * is stopped first.
     *
     * @param request The chat, config, question and new text (null to ask it again unchanged).
     * @return An empty response; the answer is delivered by polling.
     */
    public AskStroomAiResponse editMessage(final EditAiMessageRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        aiService.verifyOwnership(chatId);
        final List<AiChatMessage> messages = aiService.getMessages(chatId);
        final AiChatMessage question = messages.stream()
                .filter(msg -> msg.getId() == request.getMessageId())
                .filter(msg -> msg.getMessageType() == AiMessageType.USER_MESSAGE)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Only your own questions in this chat can be edited."));
        final String newText = NullSafe.nonBlankStringElse(request.getMessage(), question.getMessage());

        stopAndWait(chatId, AiChatRunStopReason.EDITED);

        // Only what is there now is removed: anything added once the new run has started belongs to it.
        final List<AiChatMessage> toRemove = messages.stream()
                .filter(msg -> msg.getId() >= question.getId())
                .toList();
        final AskStroomAiRequest edited = new AskStroomAiRequest(request.getAiChat(), request.getConfig(), null,
                newText);
        // The removal is done by the new run, so that nothing else can start in the chat while it happens.
        runManager.start(chatId, run -> {
            removeMessages(toRemove);
            chatCompactor.onTurnChanged(chatId, question.getTurnId());
            aiService.storeMessage(chatId, AiMessageType.USER_MESSAGE, newText);
            answerQuestion(chatId, edited);
        });
        return new AskStroomAiResponse(null);
    }

    /**
     * Compacts a chat now, in a run of its own: earlier turns are replaced, in what the model sees, by their
     * digests and a summary written by the model. The latest turns are kept in full, and nothing is deleted.
     *
     * @param request The chat, the config giving the model, and what the summary should focus on.
     * @return An empty response; the result appears in the chat.
     * @throws AiChatRunManager.AiChatBusyException if the chat is busy.
     */
    public AskStroomAiResponse compactChat(final CompactAiChatRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        aiService.verifyOwnership(chatId);
        final OpenAIModelDoc modelDoc = getModelDocForCompaction(request.getConfig());
        final ChatModel chatModel = modelDoc != null
                ? getChatModel(modelDoc)
                : null;
        runManager.start(chatId, run -> {
            if (!chatCompactor.compactNow(chatId, modelDoc, chatModel, request.getInstructions())) {
                aiService.storeMessage(chatId, AiMessageType.WARNING, NOTHING_TO_COMPACT);
            }
        });
        return new AskStroomAiResponse(null);
    }

    /**
     * Answers something the agent asked the user's approval for.
     *
     * @param request The chat, the request being answered, and the answer.
     * @return True if what was asked for has been done.
     */
    public Boolean answerApproval(final AnswerAiApprovalRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        aiService.verifyOwnership(chatId);
        final AiChatMessage message = aiService.getMessages(chatId).stream()
                .filter(m -> m.getId() == request.getMessageId()
                             && m.getMessageType() == AiMessageType.APPROVAL_REQUEST)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("There is no request for approval "
                                                        + request.getMessageId() + " in this chat."));
        if (ToolApprovalService.read(message).isPresent()) {
            final ToolApprovalRequest.Status status = toolApprovalService.answer(chatId, message, request);
            if (status == ToolApprovalRequest.Status.ANSWERED) {
                carryOn(chatId);
            }
            return status != ToolApprovalRequest.Status.EXPIRED;
        }
        return memoryService.answerApproval(chatId, request.getMessageId(), request.isApproved())
               == ApprovalStatus.APPROVED;
    }

    /**
     * @param chatId The chat.
     * @return The memory the chat can see, and the changes the agent has made to memory in it.
     */
    public AiMemoryListResponse listMemory(final int chatId) {
        aiService.verifyOwnership(chatId);
        return new AiMemoryListResponse(memoryService.listAll(chatId), memoryService.listChanges(chatId));
    }

    /**
     * Saves a piece of memory the user has written or edited.
     */
    public AiMemory saveMemory(final SaveAiMemoryRequest request) {
        final int chatId = NullSafe.getOrElse(request.getAiChat(), AiChat::getId, 0);
        aiService.verifyOwnership(chatId);
        return memoryService.saveByUser(chatId, request.getPath(), request.getContent(), request.getVersion());
    }

    /**
     * Deletes a piece of memory, or all memory in a folder.
     */
    public Boolean deleteMemory(final DeleteAiMemoryRequest request) {
        final int chatId = NullSafe.getOrElse(request.getAiChat(), AiChat::getId, 0);
        aiService.verifyOwnership(chatId);
        memoryService.deleteByUser(chatId, request.getPath(), request.getVersion());
        return true;
    }

    /**
     * Undoes a change the agent made to memory.
     */
    public Boolean undoMemoryChange(final UndoAiMemoryChangeRequest request) {
        final int chatId = NullSafe.getOrElse(request.getAiChat(), AiChat::getId, 0);
        aiService.verifyOwnership(chatId);
        memoryService.undoChange(chatId, request.getMessageId());
        return true;
    }

    /**
     * @param chatId The chat.
     * @return The decisions the user has made about tools in the chat, and whether 'approve everything' is on.
     */
    public AiToolApprovalsResponse listToolApprovals(final int chatId) {
        aiService.verifyOwnership(chatId);
        return new AiToolApprovalsResponse(toolApprovalService.list(chatId), aiDao.isApproveEverything(chatId),
                NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::isAllowApproveEverything, false));
    }

    /**
     * Removes a decision the user made about a tool in a chat.
     */
    public Boolean removeToolApproval(final int chatId, final int approvalId) {
        aiService.verifyOwnership(chatId);
        return toolApprovalService.remove(chatId, approvalId);
    }

    /**
     * Removes every decision the user made about tools in a chat.
     */
    public Boolean resetToolApprovals(final int chatId) {
        aiService.verifyOwnership(chatId);
        toolApprovalService.reset(chatId);
        return true;
    }

    /**
     * Turns 'approve everything' on or off for a chat. It can only be turned on if an administrator allows it.
     *
     * @param request The chat, and whether to turn it on.
     * @return True once done.
     */
    public Boolean setApproveEverything(final SetAiApproveEverythingRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        aiService.verifyOwnership(chatId);
        if (request.isEnabled()
            && !NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::isAllowApproveEverything, false)) {
            throw new RuntimeException("An administrator has not allowed 'approve everything'.");
        }
        aiDao.setApproveEverything(chatId, request.isEnabled());
        return true;
    }

    /**
     * Carries on answering the chat's latest question, e.g. once the user has answered a request to approve tool
     * calls: in the run that is waiting for them, or in a new run if that run has stopped.
     */
    private void carryOn(final int chatId) {
        final AskStroomAiRequest request = continuingRequest(chatId);
        try {
            runManager.continueRun(chatId, run -> answerQuestion(chatId, request));
        } catch (final AiChatRunManager.AiChatBusyException e) {
            if (runDao.getActive(chatId).isPresent()) {
                throw e;
            }
            runManager.start(chatId, run -> answerQuestion(chatId, request));
        }
    }

    /**
     * @return A request to carry on answering the chat's latest question with the model it was being answered with.
     */
    private AskStroomAiRequest continuingRequest(final int chatId) {
        final AiChat chat = aiService.getChat(chatId);
        final String question = aiService.getMessages(chatId).reversed().stream()
                .filter(m -> m.getMessageType() == AiMessageType.USER_MESSAGE)
                .map(AiChatMessage::getMessage)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("There is no question to carry on with in this chat."));
        final String modelUuid = aiDao.getUsage(chatId).modelDocUuid();
        final AskStroomAiConfig defaultConfig = defaultConfigProvider.get();
        final AskStroomAiConfig config = modelUuid == null || defaultConfig == null
                ? defaultConfig
                : defaultConfig.copy().modelRef(new DocRef(OpenAIModelDoc.TYPE, modelUuid)).build();
        return new AskStroomAiRequest(chat, config, null, question);
    }

    /**
     * @return The model to write a compaction's summary with. Separate so tests need no model.
     */
    OpenAIModelDoc getModelDocForCompaction(final AskStroomAiConfig config) {
        return getModelDoc(config);
    }

    /**
     * Undoes a chat's latest compaction that is in use, so the model sees the turns it covered as an earlier
     * compaction left them (or in full). If the chat is still too large, the next question compacts it again.
     *
     * @param request The chat, and the compaction to undo.
     * @return True once undone.
     * @throws AiChatRunManager.AiChatBusyException if the chat is busy.
     */
    public AiContextUsage undoCompaction(final UndoAiCompactionRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        aiService.verifyOwnership(chatId);
        if (runDao.getActive(chatId).isPresent()) {
            throw new AiChatRunManager.AiChatBusyException(chatId);
        }
        final AiChatMessage latest = ChatCompactor.currentCompactionMessage(aiService.getMessages(chatId))
                .orElse(null);
        if (latest == null || latest.getId() != request.getMessageId()) {
            throw new RuntimeException("Only the latest compaction of a chat can be undone.");
        }
        aiService.deleteMessage(latest.getId());
        return contextAssembler.estimateUsage(chatId);
    }

    /**
     * Sets how long an edit waits for the chat's run to stop. For testing.
     */
    void setStopWait(final Duration stopWait) {
        this.stopWait = stopWait;
    }

    /**
     * Stops the chat's active run, if it has one, and waits for it to stop.
     *
     * @throws AiChatRunManager.AiChatBusyException if it does not stop in time.
     */
    private void stopAndWait(final int chatId, final AiChatRunStopReason reason) {
        if (runDao.getActive(chatId).isEmpty()) {
            return;
        }
        runManager.stop(chatId, reason);
        final long deadline = System.currentTimeMillis() + stopWait.toMillis();
        while (runDao.getActive(chatId).isPresent()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AiChatRunManager.AiChatBusyException(chatId);
            }
            ThreadUtil.sleepIgnoringInterrupts(STOP_POLL_INTERVAL.toMillis());
        }
    }

    private void removeMessages(final List<AiChatMessage> messages) {
        for (final AiChatMessage message : messages.reversed()) {
            if (message.getAttachmentId() != null) {
                deleteAttachment(message.getChatId(), message.getAttachmentId());
            } else {
                aiService.deleteMessage(message.getId());
            }
        }
    }

    /**
     * Deletes an attachment (with the message that refers to it), its profile, and their local files.
     */
    private void deleteAttachment(final int chatId, final int attachmentId) {
        workspaceDao.findFile(chatId, profilePath(attachmentId)).ifPresent(profileId -> {
            aiService.deleteAttachment(profileId);
            attachmentFileStore.deleteAttachmentFile(profileId);
        });
        aiService.deleteAttachment(attachmentId);
        attachmentFileStore.deleteAttachmentFile(attachmentId);
    }

    /**
     * If the chat has a run working on a question, stores the message for the run to pick up at its next safe
     * point.
     *
     * @return True if the message was given to a run, false if there is no run to give it to.
     */
    private boolean steer(final int chatId, final String message) {
        final AiChatRun active = runDao.getActive(chatId).orElse(null);
        if (active == null || active.getStatus() == AiChatRunStatus.STOPPING) {
            return false;
        }
        if (active.getStatus() == AiChatRunStatus.WAITING_FOR_USER) {
            // A message sent instead of answering a request for approval denies the calls, with the message as
            // the reason, and the run carries on with it.
            aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, message);
            toolApprovalService.pending(chatId).ifPresent(pending ->
                    toolApprovalService.denyAll(pending, "The user sent a message instead: " + message));
            carryOn(chatId);
            return true;
        }
        final AiChatMessage steerMessage = aiService.storeMessage(chatId, AiMessageType.STEER_MESSAGE, message);
        if (runDao.getActive(chatId).isEmpty()) {
            // The run finished as the message arrived, so it will not see it. Ask it as a question instead.
            aiService.deleteMessage(steerMessage.getId());
            return false;
        }
        LOGGER.debug(() -> "Steering run " + active.getId() + " of chat " + chatId);
        return true;
    }

    /**
     * Resumes a chat whose last run stopped before finishing (stopped, interrupted or failed), by answering its
     * last question again in a new run. The model is told that an earlier attempt did not finish and that
     * anything it did may already have happened.
     *
     * @param request The chat and config. Any message or context in the request is ignored.
     * @return An empty response; the answer is delivered by polling.
     */
    public AskStroomAiResponse resumeRun(final AskStroomAiRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        aiService.verifyOwnership(chatId);
        final AiChatRun previous = runDao.getLatest(chatId).orElse(null);
        if (!AiChatRunManager.isResumable(previous)) {
            throw new RuntimeException("There is nothing to resume in this chat.");
        }
        final List<AiChatMessage> messages = aiService.getMessages(chatId);
        final AiChatMessage question = messages.reversed().stream()
                .filter(msg -> msg.getMessageType() == AiMessageType.USER_MESSAGE)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("There is no question to resume in this chat."));

        final AskStroomAiRequest resumed = new AskStroomAiRequest(
                request.getAiChat(), request.getConfig(), null, resumeNote(previous) + question.getMessage());
        runManager.start(chatId, run -> answerQuestion(chatId, resumed));
        return new AskStroomAiResponse(null);
    }

    /**
     * @return A note to put before a question that is being answered again, saying why the earlier attempt
     * did not finish.
     */
    static String resumeNote(final AiChatRun previous) {
        final String how = previous.getStopReason() != null
                ? previous.getStopReason().getDisplayValue().toLowerCase()
                : previous.getStatus().getDisplayValue().toLowerCase();
        return "(An earlier attempt to answer this did not finish: " + how + ". Anything it did, such as changes "
               + "made with tools, may already have happened, so check the current state before repeating an "
               + "action.)\n\n";
    }

    /**
     * Works out the answer to the question in the request and stores it, or the error, in the chat.
     */
    private void answerQuestion(final int chatId, final AskStroomAiRequest request) {
        String responseText;
        try {
            responseText = processQuestion(request);

            if (responseText != null && !CANCELLED_MESSAGE.equals(responseText)) {
                aiService.storeMessage(chatId, AiMessageType.AI_RESPONSE, responseText);
                auditLog.answer(chatId, request.getMessage(), responseText, runManager.isUnattended(chatId));
                chatCompactor.writeDigest(chatId);
                final String rt = responseText;
                LOGGER.debug(() -> "Response stored for chatId=" + chatId
                                   + " responseLength=" + rt.length());
            }
        } catch (final TaskTerminatedException | AiChatRunManager.WaitingForUserException e) {
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            if (isTimeoutError(e)) {
                responseText = "The AI model took too long to respond and the request "
                               + "timed out. This can happen with complex questions, large "
                               + "context, or reasoning models. You can increase the timeout "
                               + "in the OpenAI Model document's HTTP Client Configuration "
                               + "settings (timeout field).";
            } else {
                responseText = e.getMessage();
            }
            aiService.storeMessage(chatId, AiMessageType.ERROR, responseText);
        }
    }

    // ---------------------------------------------------------------------
    // Attachment lifecycle
    // ---------------------------------------------------------------------

    /**
     * Creates an attachment record and starts downloading the data.
     * For GeneralTableContext, data is already on the server so we convert synchronously.
     * For Dashboard/Query contexts, we submit an async download task.
     */
    private void createAttachment(final int chatId,
                                  final AskStroomAiContext context,
                                  final AskStroomAiConfig config) {
        final AiAttachmentType attachmentType = switch (context) {
            case final DashboardTableContext dashboardTableContext -> AiAttachmentType.DASHBOARD;
            case final QueryTableContext queryTableContext -> AiAttachmentType.QUERY;
            case final GeneralTableContext generalTableContext -> AiAttachmentType.GENERAL;
            default -> throw new IllegalStateException("Unsupported context type: " + context.getClass().getName());
        };

        LOGGER.debug(() -> "createAttachment: chatId=" + chatId
                           + " type=" + attachmentType
                           + " description='" + context.getDescription() + "'");

        final String contextJson = JsonUtil.writeValueAsString(context);
        final AiChatAttachment attachment = aiService.createAttachment(chatId, attachmentType, contextJson);

        // Store an ATTACHMENT message in the chat history linking to this attachment.
        aiService.storeMessage(chatId, AiMessageType.ATTACHMENT, attachment.getId(),
                context.getDescription());

        if (context instanceof final GeneralTableContext generalTableContext) {
            // Synchronous — data is already in memory, just convert to markdown.
            processGeneralAttachment(attachment.getId(), chatId, generalTableContext,
                    getTableAnalysisConfig(config));
        } else {
            // Async download for Dashboard/Query contexts.
            submitAsyncDownload(attachment.getId(), chatId, context, config);
        }
    }

    /**
     * @return True if the attachment can still be used. If it has expired, or the user no longer has access to
     * the data it came from, its content is removed and the model is told it has gone.
     */
    private boolean isAvailable(final AiChatAttachment attachment) {
        try {
            workspaceStore.checkAvailable(attachment.getId());
            return true;
        } catch (final WorkspaceFileUnavailableException e) {
            LOGGER.debug(e::getMessage);
            return false;
        }
    }

    /**
     * @return The UUID of the document whose data an attached table holds, so the user's access to it can be
     * checked again whenever the table is used: the data source of a dashboard table, or the query document of a
     * query table.
     */
    static String sourceDocUuid(final AskStroomAiContext context) {
        return switch (context) {
            case final DashboardTableContext dashboard -> NullSafe.get(dashboard.getSearchRequest(),
                    DashboardSearchRequest::getSearch, Search::getDataSourceRef, DocRef::getUuid);
            case final QueryTableContext query -> NullSafe.get(query.getSearchRequest(),
                    QuerySearchRequest::getSearchRequestSource, SearchRequestSource::getOwnerDocRef,
                    DocRef::getUuid);
            default -> null;
        };
    }

    /**
     * Converts GeneralTableContext (in-memory table data) directly to a markdown file
     * in the attachment file store and marks the attachment as READY.
     */
    private void processGeneralAttachment(final int attachmentId,
                                          final int chatId,
                                          final GeneralTableContext generalTableContext,
                                          final TableAnalysisConfig tableAnalysisConfig) {
        LOGGER.debug(() -> "processGeneralAttachment: attachmentId=" + attachmentId
                           + " cols=" + generalTableContext.getColumns().size()
                           + " rows=" + generalTableContext.getRows().size());
        try {
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.DOWNLOADING,
                    null, null, null, false);

            final Path mdFile = attachmentFileStore.createAttachmentFile(attachmentId);
            final int[] rowCountHolder = {0};
            LOGGER.logDurationIfDebugEnabled(() -> {
                try (final BufferedWriter writer = Files.newBufferedWriter(mdFile)) {
                    // Write markdown header
                    final List<String> columns = generalTableContext.getColumns();
                    writer.write(columns.stream()
                            .map(col -> "| " + escapeMarkdownCell(col) + " ")
                            .collect(Collectors.joining()));
                    writer.write("|\n");
                    // Write separator row
                    writer.write(columns.stream()
                            .map(val -> "| --- ")
                            .collect(Collectors.joining()));
                    writer.write("|\n");
                    // Write rows
                    final int maxRows = tableAnalysisConfig.getMaxTotalRows();
                    for (final List<String> rowValues : generalTableContext.getRows()) {
                        if (rowCountHolder[0] >= maxRows) {
                            break;
                        }
                        writer.write(rowValues.stream()
                                .map(val -> "| " + escapeMarkdownCell(val) + " ")
                                .collect(Collectors.joining()));
                        writer.write("|\n");
                        rowCountHolder[0]++;
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, () -> "processGeneralAttachment attachmentId=" + attachmentId
                     + " rows=" + rowCountHolder[0]);
            final int rowCount = rowCountHolder[0];
            final boolean truncated = rowCount < generalTableContext.getRows().size();

            final String description = generalTableContext.getDescription()
                                       + " (" + rowCount + " rows, "
                                       + generalTableContext.getColumns().size() + " cols"
                                       + (truncated
                    ? ", truncated to limit"
                    : "") + ")";
            storeAttachment(chatId, attachmentId, mdFile, description, null);
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.READY,
                    rowCount, description, null, truncated);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.ERROR,
                    null, null, e.getMessage(), false);
            attachmentFileStore.deleteAttachmentFile(attachmentId);
        }
    }

    /**
     * Stores an attachment's table in the chat's workspace, so it can be read on any node, along with a profile of
     * it made in code. Done before the attachment is marked as ready.
     */
    private void storeAttachment(final int chatId,
                                 final int attachmentId,
                                 final Path tableFile,
                                 final String description,
                                 final String sourceDocUuid) {
        workspaceStore.persist(attachmentId, attachmentPath(attachmentId), tableFile);
        workspaceStore.setRetention(attachmentId, sourceDocUuid);
        try (final Reader reader = Files.newBufferedReader(tableFile, StandardCharsets.UTF_8)) {
            final String profile = TableProfiler.profile(reader, description);
            final int profileId = workspaceStore.createFile(chatId, AiAttachmentType.WORKSPACE_FILE,
                    profilePath(attachmentId), null, profile);
            workspaceStore.setRetention(profileId, sourceDocUuid);
        } catch (final IOException | RuntimeException e) {
            // The table is still usable without its profile.
            LOGGER.warn(() -> "Unable to profile attachment " + attachmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * @return The path of an attached table in its chat's workspace.
     */
    static String attachmentPath(final int attachmentId) {
        return "/attachments/" + attachmentId + ".md";
    }

    /**
     * @return The path of the profile of an attached table in its chat's workspace.
     */
    static String profilePath(final int attachmentId) {
        return "/attachments/" + attachmentId + ".profile.md";
    }

    /**
     * Submits an async task to download table data from a Dashboard or Query search
     * and save the markdown to the attachment file store.
     */
    private void submitAsyncDownload(final int attachmentId,
                                     final int chatId,
                                     final AskStroomAiContext context,
                                     final AskStroomAiConfig config) {
        final Runnable runnable = taskContextFactory.context(
                "Download search results for AI analysis",
                taskContext -> {
                    try {
                        info(taskContext, () -> "Async download started: attachmentId=" + attachmentId
                                                + " chatId=" + chatId
                                                + " contextType=" + context.getClass().getSimpleName());
                        aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.DOWNLOADING,
                                null, null, null, false);

                        final TableAnalysisConfig tableAnalysisConfig = getTableAnalysisConfig(config);
                        final Supplier<ResourceGeneration> downloadSupplier =
                                buildDownloadSupplier(context, tableAnalysisConfig);
                        final ResourceGeneration resourceGeneration = LOGGER.logDurationIfDebugEnabled(
                                downloadSupplier,
                                rg -> "Download completed for attachmentId=" + attachmentId);
                        final ResourceKey resourceKey = resourceGeneration.getResourceKey();

                        try {
                            final Path tempFile = resourceStore.getTempFile(resourceKey);
                            final Path targetFile = attachmentFileStore.createAttachmentFile(attachmentId);
                            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

                            // Count rows by streaming the markdown file
                            // (skip header line and separator line).
                            final int maxRows = tableAnalysisConfig.getMaxTotalRows();
                            int rowCount = 0;
                            try (final BufferedReader reader = Files.newBufferedReader(targetFile)) {
                                reader.readLine(); // skip markdown header
                                reader.readLine(); // skip separator row
                                while (reader.readLine() != null) {
                                    rowCount++;
                                }
                            }

                            // Truncated if we got more rows than the configured max
                            // (we requested maxRows+1 to probe for overflow).
                            final boolean truncated = rowCount > maxRows;
                            final int reportedRowCount;

                            if (truncated) {
                                // Rewrite the file without the overflow probe row.
                                final Path trimmedFile = targetFile.resolveSibling(
                                        targetFile.getFileName() + ".tmp");
                                try (final BufferedReader reader = Files.newBufferedReader(targetFile);
                                        final BufferedWriter writer = Files.newBufferedWriter(trimmedFile)) {
                                    int linesWritten = 0;
                                    String line;
                                    // Keep header + separator + maxRows data lines
                                    final int maxLines = maxRows + 2;
                                    while ((line = reader.readLine()) != null) {
                                        if (linesWritten < maxLines) {
                                            writer.write(line);
                                            writer.newLine();
                                            linesWritten++;
                                        } else {
                                            break;
                                        }
                                    }
                                }
                                Files.move(trimmedFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                reportedRowCount = maxRows;
                            } else {
                                reportedRowCount = rowCount;
                            }

                            final int colCount;
                            try (final BufferedReader reader = Files.newBufferedReader(targetFile)) {
                                final String header = reader.readLine();
                                colCount = header != null
                                        ? parseMarkdownColumnCount(header)
                                        : 0;
                            }

                            final String description = context.getDescription()
                                                       + " (" + reportedRowCount + " rows, " + colCount + " cols"
                                                       + (truncated
                                    ? ", truncated to limit"
                                    : "") + ")";

                            info(taskContext, () -> "Async download finished: attachmentId=" + attachmentId
                                                    + " rows=" + reportedRowCount + " cols=" + colCount
                                                    + " truncated=" + truncated);
                            storeAttachment(chatId, attachmentId, targetFile, description, sourceDocUuid(context));
                            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.READY,
                                    reportedRowCount, description, null, truncated);
                        } finally {
                            try {
                                resourceStore.deleteTempFile(resourceKey);
                            } catch (final Exception e) {
                                LOGGER.debug(() -> "Failed to delete temp file", e);
                            }
                        }
                    } catch (final Exception e) {
                        LOGGER.debug(e::getMessage, e);
                        aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.ERROR,
                                null, null, e.getMessage(), false);
                        attachmentFileStore.deleteAttachmentFile(attachmentId);
                    }
                });
        executorProvider.get().execute(runnable);
    }

    private void info(final TaskContext taskContext, final Supplier<String> message) {
        taskContext.info(message);
        LOGGER.debug(message);
    }

    /**
     * Builds the download supplier for Dashboard or Query contexts.
     * Applies row-limiting via the existing requestedRange on the search request.
     */
    private Supplier<ResourceGeneration> buildDownloadSupplier(final AskStroomAiContext context,
                                                               final TableAnalysisConfig tableAnalysisConfig) {
        // Request one extra row beyond the limit to detect truncation.
        // If we get maxRows+1 rows back, we know the data is genuinely truncated.
        final long maxRows = tableAnalysisConfig.getMaxTotalRows();
        final OffsetRange probeRange = new OffsetRange(0L, maxRows + 1);

        if (context instanceof final DashboardTableContext ctx) {
            return () -> {
                final DashboardSearchRequest searchRequest = ctx.getSearchRequest();
                // Override requestedRange on each TableResultRequest to limit rows.
                final List<ComponentResultRequest> limitedRequests =
                        searchRequest.getComponentResultRequests().stream()
                                .map(crr -> {
                                    if (crr instanceof final TableResultRequest trr) {
                                        return trr.copy()
                                                .requestedRange(probeRange)
                                                .build();
                                    }
                                    return crr;
                                }).toList();

                final DashboardSearchRequest limitedSearchRequest = searchRequest.copy()
                        .componentResultRequests(limitedRequests)
                        .build();

                final String componentId = limitedRequests.stream()
                        .filter(crr -> crr instanceof TableResultRequest)
                        .map(ComponentResultRequest::getComponentId)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No table component found"));
                final DownloadSearchResultsRequest downloadRequest = new DownloadSearchResultsRequest(
                        limitedSearchRequest, componentId, DownloadSearchResultFileType.MARKDOWN,
                        false, false, 100);
                return dashboardService.downloadSearchResults(downloadRequest);
            };
        } else if (context instanceof final QueryTableContext ctx) {
            return () -> {
                final QuerySearchRequest searchRequest = ctx.getSearchRequest();
                final QuerySearchRequest limitedSearchRequest = searchRequest.copy()
                        .requestedRange(probeRange)
                        .build();
                final DownloadQueryResultsRequest downloadRequest = new DownloadQueryResultsRequest(
                        limitedSearchRequest, DownloadSearchResultFileType.MARKDOWN, false, 100);
                return queryService.downloadSearchResults(downloadRequest);
            };
        }
        throw new IllegalStateException("Cannot build download supplier for: " + context.getClass().getName());
    }

    // ---------------------------------------------------------------------
    // Question processing
    // ---------------------------------------------------------------------

    /**
     * Core question processing. Creates a single WORKING message that is updated in place
     * as processing progresses, then deleted when complete. Waits for any in-flight
     * attachments, then uses optimistic send with progressive trim and summarisation:
     * <ol>
     *     <li>Try sending full conversation history to the LLM.</li>
     *     <li>On context overflow, summarise the dropped messages and retry with fewer.</li>
     *     <li>If history is fully trimmed and it still overflows, fall back to batch processing.</li>
     * </ol>
     */
    private String processQuestion(final AskStroomAiRequest request) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        if (chatId == null) {
            throw new RuntimeException("Cannot process question without a chat");
        }

        LOGGER.debug(() -> "processQuestion: chatId=" + chatId);

        final AskStroomAiConfig config = request.getConfig();
        final OpenAIModelDoc modelDoc = getModelDoc(config);
        final ChatModel chatModel = getChatModel(modelDoc);
        final int maxContextTokens = modelDoc.getMaxContextWindowTokens();
        final boolean debugEnabled = NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAiConfig::isEnableDebugDetail,
                AskStroomAiConfig.DEFAULT_ENABLE_DEBUG_DETAIL);

        // Clear anything left behind by a previous question that did not get to clean up after
        // itself, or the chat would look like it was working forever.
        aiService.deleteWorkingMessages(chatId);

        // Create a single WORKING message that will be updated in place.
        final AiChatMessage workingMsg = aiService.storeMessage(
                chatId, AiMessageType.WORKING, "Working...");
        final int workingMessageId = workingMsg.getId();

        // Registered for the whole question rather than just for batch analysis, so that a stop issued
        // while waiting for attachments, or between retries, is seen at all.
        final AtomicBoolean cancelled = runManager.stopFlag(chatId);

        // A model with tools turned off (e.g. one that cannot call tools) gets a plain chat.
        try (final McpToolset toolset = modelDoc.isToolsEnabled()
                ? mcpToolServerFactory.createToolset(modelDoc, chatId)
                : McpToolset.EMPTY) {
            if (!toolset.isEmpty()) {
                LOGGER.debug(() -> "processQuestion: MCP tool servers active for chatId=" + chatId);
            }
            // Servers that could not be reached are left out, and the user is told why.
            toolset.getWarnings().forEach(warning ->
                    aiService.storeMessage(chatId, AiMessageType.WARNING, warning));

            // Wait for any DOWNLOADING attachments to become READY.
            LOGGER.logDurationIfDebugEnabled(
                    () -> waitForAttachments(chatId, workingMessageId),
                    () -> "waitForAttachments chatId=" + chatId);

            // Check for READY attachments.
            final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);
            final List<AiChatAttachment> readyAttachments = attachments.stream()
                    .filter(a -> a.getStatus() == AiAttachmentStatus.READY)
                    .filter(a -> a.getAttachmentType() == null || a.getAttachmentType().isTableContext())
                    .filter(this::isAvailable)
                    .toList();

            LOGGER.debug(() -> "processQuestion: chatId=" + chatId
                               + " readyAttachments=" + readyAttachments.size());

            final Map<Integer, AiChatAttachment> attachmentMap = readyAttachments.stream()
                    .collect(Collectors.toMap(AiChatAttachment::getId, a -> a));
            // Messages the user sends after this are steering messages for the run to pick up.
            final int steerAfterMessageId = aiService.getMessages(chatId).stream()
                    .mapToInt(AiChatMessage::getId)
                    .max()
                    .orElse(0);
            final ContextBudget budget = chatCompactor.budget(modelDoc);

            // Accumulate debug detail across all attempts.
            final StringBuilder debugLog = debugEnabled
                    ? new StringBuilder()
                    : null;

            // If the model says the request is too large, compact the chat as far as it will go and try once
            // more.
            for (int attempt = 1; ; attempt++) {
                if (cancelled.get()) {
                    LOGGER.info(() -> "processQuestion: cancelled before attempt for chatId=" + chatId);
                    return CANCELLED_MESSAGE;
                }

                final int currentAttempt = attempt;
                final List<ChatMessage> messages = chatCompactor.assemble(
                        request, chatId, attachmentMap, modelDoc, chatModel,
                        attempt == 1
                                ? CompactionMode.AUTO
                                : CompactionMode.FORCE,
                        null);

                LOGGER.debug(() -> "processQuestion: attempt " + currentAttempt
                                   + " messages=" + messages.size()
                                   + " chatId=" + chatId);

                // Proactive check: if the context window size is configured and we
                // have attachments, estimate the total token count. If it exceeds
                // the context window, go straight to batch processing to avoid a
                // wasteful round-trip that will fail.
                if (maxContextTokens > 0 && !readyAttachments.isEmpty() && currentAttempt == 1) {
                    final int estimatedTokens = estimateTokenCount(messages);
                    if (estimatedTokens > maxContextTokens) {
                        LOGGER.info(() -> "Estimated " + estimatedTokens
                                          + " tokens exceeds context window of " + maxContextTokens
                                          + " for chatId=" + chatId
                                          + ", going directly to batch processing");
                        if (debugLog != null) {
                            debugLog.append("### Proactive Overflow Detection\n\n")
                                    .append("Estimated tokens: ").append(estimatedTokens)
                                    .append(", context window: ").append(maxContextTokens)
                                    .append("\n\n---\n\n");
                        }
                        final String result = analyseWithAttachments(request, chatModel,
                                readyAttachments, chatId, workingMessageId, debugLog);
                        if (debugLog != null) {
                            storeDebugDetail(chatId, debugLog.toString());
                        }
                        return result;
                    }
                }

                try {
                    final ChatResponse chatResponse = processUnified(
                            chatModel, modelDoc, chatId, messages, currentAttempt, toolset, workingMessageId,
                            cancelled, debugLog, steerAfterMessageId, budget);
                    final String responseText = chatResponse.aiMessage().text();

                    // Capture successful attempt.
                    if (debugLog != null) {
                        debugLog.append("### Attempt ")
                                .append(currentAttempt)
                                .append(" (")
                                .append(messages.size())
                                .append(" messages) — SUCCESS\n\n")
                                .append(formatMessagesAsDebugDetail(messages, responseText));
                        storeDebugDetail(chatId, debugLog.toString());
                    }

                    return responseText;

                } catch (final Exception e) {
                    // A terminated task, or a stop flipped mid-call, is a deliberate stop rather
                    // than a failure. processUnified throws TaskTerminatedException when the task
                    // is terminated before the model call; treat both the same friendly way as a
                    // stop caught between attempts, rather than surfacing a raw error to the user.
                    if (e instanceof TaskTerminatedException || cancelled.get()) {
                        LOGGER.info(() -> "processQuestion: terminated/cancelled for chatId=" + chatId);
                        return CANCELLED_MESSAGE;
                    }

                    if (!ChatCompactor.isContextOverflowError(e)) {
                        throw e;
                    }

                    // Capture failed attempt.
                    if (debugLog != null) {
                        debugLog.append("### Attempt ")
                                .append(currentAttempt)
                                .append(" (")
                                .append(messages.size())
                                .append(" messages) — OVERFLOW\n\n")
                                .append(formatMessagesAsDebugDetail(messages, null))
                                .append("\n---\n\n");
                    }

                    if (currentAttempt >= 2) {
                        // Compacted as far as it will go - fall back to batch if there are attachments.
                        if (!readyAttachments.isEmpty()) {
                            LOGGER.info(() -> "Context overflow after compacting chatId=" + chatId
                                              + ", falling back to batch processing");
                            final String result = analyseWithAttachments(request, chatModel,
                                    readyAttachments, chatId, workingMessageId,
                                    debugLog);
                            if (debugLog != null) {
                                storeDebugDetail(chatId, debugLog.toString());
                            }
                            return result;
                        }
                        // No attachments and still overflowing. There is no partial answer to give
                        // here - nothing has been produced yet - so at least say what was tried.
                        throw new RuntimeException(
                                "The question is too large for the model's context window, even with the earlier "
                                + "conversation compacted. Try a shorter question, start a new chat, or raise "
                                + "'Max Context Window Tokens' on the model document. "
                                + "The model said: " + e.getMessage(), e);
                    }
                    LOGGER.info(() -> "Context overflow for chatId=" + chatId + ", compacting and retrying");
                }
            }
        } finally {
            // Always clean up the WORKING message when processing is done.
            try {
                aiService.deleteMessage(workingMessageId);
            } catch (final Exception e) {
                LOGGER.debug(() -> "Failed to delete working message " + workingMessageId, e);
            }
        }
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
    static List<AiChatMessage> selectHistoryForModel(final List<AiChatMessage> history) {
        return ContextAssembler.selectHistoryForModel(history);
    }


    /**
     * Sends the pre-built message list to the LLM and returns the full ChatResponse
     * so callers can access both the response text and any thinking/reasoning content.
     * <p>
     * Runs via {@link McpAgentChat}: the model is offered the built-in tools plus any MCP tools/resources
     * from its configured tool servers, tool calls are executed and fed back, and the loop repeats until
     * the model answers without calling a tool, the iteration cap is hit, or the task is stopped. With no
     * tools it is a single plain call.
     */
    private ChatResponse processUnified(final ChatModel chatModel,
                                        final OpenAIModelDoc modelDoc,
                                        final int chatId,
                                        final List<ChatMessage> messages,
                                        final int attempt,
                                        final McpToolset toolset,
                                        final int workingMessageId,
                                        final AtomicBoolean cancelled,
                                        final StringBuilder debugLog,
                                        final int steerAfterMessageId,
                                        final ContextBudget budget) {
        return taskContextFactory.contextResult(
                "AI data analysis",
                taskContext -> {
                    taskContext.info(() -> "Chat Id: " + chatId);

                    if (taskContext.isTerminated()) {
                        throw new TaskTerminatedException();
                    }

                    LOGGER.debug(() -> "processUnified: sending " + messages.size()
                                       + " messages to LLM for chatId=" + chatId
                                       + " (attempt " + attempt + ")");

                    // The agent loop offers the built-in tools plus any MCP tools; with no tools at all
                    // it degrades to a single plain call.
                    return mcpAgentChat.chat(chatModel, modelDoc, chatId, messages, toolset,
                            workingMessageId, cancelled, taskContext, attempt, debugLog, steerAfterMessageId,
                            budget);
                }).get();
    }

    /**
     * Performs parallel batch analysis over all READY attachments, reading markdown data
     * from the attachment file store. Each batch is submitted to the LLM concurrently.
     * Results are merged in a single pass. Used as a fallback when the unified
     * single-call approach exceeds the model's context window.
     */
    private String analyseWithAttachments(final AskStroomAiRequest request,
                                          final ChatModel chatModel,
                                          final List<AiChatAttachment> attachments,
                                          final int chatId,
                                          final int workingMessageId,
                                          final StringBuilder debugLog) {
        return taskContextFactory.contextResult(
                "Batched AI data analysis",
                taskContext -> analyseWithAttachments(taskContext,
                        request, chatModel, attachments, chatId, workingMessageId, debugLog)).get();
    }

    private String analyseWithAttachments(final TaskContext taskContext,
                                          final AskStroomAiRequest request,
                                          final ChatModel chatModel,
                                          final List<AiChatAttachment> attachments,
                                          final int chatId,
                                          final int workingMessageId,
                                          final StringBuilder debugLog) {
        // Shares the run's stop flag, so a stop issued before batch analysis started is not lost.
        final AtomicBoolean cancelled = runManager.stopFlag(chatId);

        // Either signal means stop: the explicit user stop, or the task being terminated.
        final BooleanSupplier stopRequested = () -> cancelled.get() || taskContext.isTerminated();

        info(taskContext, () -> "Reading attachments");

        final List<TableSource> sources = attachments
                .stream()
                .map(attachment -> new TableSource(
                        "attachment " + attachment.getId() + " for chatId=" + chatId,
                        workspaceStore.localFile(attachment.getId()),
                        attachment.isTruncated()))
                .toList();

        final TableSummaryRequest summaryRequest = TableSummaryRequest
                .builder()
                .sources(sources)
                .chatModel(chatModel)
                .config(getTableAnalysisConfig(request.getConfig()))
                .query(request.getMessage())
                .context(buildConversationSummary(chatId))
                .cancelled(stopRequested)
                .progressListener(new TableSummaryProgressListener() {
                    @Override
                    public void onBatchesBuilt(final int batchCount, final int sourceCount) {
                        final String message = "Analysing " + batchCount + " batch(es) across "
                                               + sourceCount + " attachment(s)...";
                        info(taskContext, () -> message);
                        aiService.updateMessageText(workingMessageId, message);
                    }

                    @Override
                    public void onBatchStarted(final int batchNumber, final int batchCount) {
                        final String message = "Analysing batch " + batchNumber + " of " + batchCount + "...";
                        info(taskContext, () -> message);
                        aiService.updateMessageText(workingMessageId, message);
                    }

                    @Override
                    public void onCancelledAwaitingBatches() {
                        aiService.updateMessageText(workingMessageId,
                                "Cancelled - waiting briefly for batches already in progress...");
                    }

                    @Override
                    public void onCancelledBeforeMerge(final int summaryCount) {
                        aiService.updateMessageText(workingMessageId,
                                "Cancelled - summarising the " + summaryCount
                                + " batch(es) completed so far...");
                    }
                })
                .build();

        // Batching is this caller's fallback rather than its normal path, so only this caller has cause
        // to explain it. Everything else the reader needs to know is added by the summariser.
        final AnswerNotes notes = new AnswerNotes()
                .add("The attached data was too large to analyse in a single call, so it was "
                     + "processed in batches, which may not capture cross-row patterns or "
                     + "cross-table comparisons as well");

        // The chat shows whatever came back, summary or not, as that is the answer to the question.
        return tableSummariser
                .summarise(summaryRequest, notes, debugLog, this::formatMessagesAsDebugDetail)
                .text();
    }

    /**
     * Formats a list of ChatMessages and the LLM response into a readable
     * markdown string suitable for storage as a DEBUG_DETAIL message.
     * Table data in user messages is truncated to keep the detail manageable.
     */
    private String formatMessagesAsDebugDetail(final List<ChatMessage> messages,
                                               final String responseText) {
        final int MAX_TABLE_LINES = 20;
        final StringBuilder sb = new StringBuilder();

        for (final ChatMessage msg : messages) {
            if (msg instanceof final SystemMessage systemMessage) {
                sb
                        .append("**System Prompt:**\n> ")
                        .append(systemMessage.text().replace("\n", "\n> "))
                        .append("\n\n");
            } else if (msg instanceof final UserMessage userMessage) {
                final String text = userMessage.singleText();
                // Truncate table data for readability.
                final String displayText = truncateTableData(text, MAX_TABLE_LINES);
                sb
                        .append("**User:**\n")
                        .append(displayText)
                        .append("\n\n");
            } else if (msg instanceof final AiMessage aiMessage) {
                sb
                        .append("**Assistant:**\n")
                        .append(aiMessage.text())
                        .append("\n\n");
            }
        }

        if (responseText != null) {
            sb
                    .append("**Response:**\n")
                    .append(responseText)
                    .append("\n\n");
        } else {
            sb
                    .append("*(No response — context overflow)*\n\n");
        }

        return sb.toString();
    }

    /**
     * Truncates markdown table data in a message to the given number of data rows.
     * Preserves the header and separator rows, then shows up to maxLines data rows
     * with a note about truncation.
     */
    private String truncateTableData(final String text,
                                     final int maxLines) {
        // Only truncate if text contains a Markdown table (pipe-delimited lines).
        if (!text.contains("| ---")) {
            return text;
        }

        final String[] lines = text.split("\n");
        final StringBuilder result = new StringBuilder();
        boolean inTable = false;
        int dataRowCount = 0;
        boolean truncated = false;

        for (final String line : lines) {
            if (line.startsWith("|")) {
                if (!inTable) {
                    inTable = true;
                    dataRowCount = 0;
                    result
                            .append(line)
                            .append('\n');
                    continue;
                }
                // Separator row (| --- | --- |).
                if (line.contains("---")) {
                    result
                            .append(line)
                            .append('\n');
                    continue;
                }
                // Data row.
                dataRowCount++;
                if (dataRowCount <= maxLines) {
                    result
                            .append(line)
                            .append('\n');
                } else if (!truncated) {
                    truncated = true;
                    result
                            .append("| *... (remaining rows truncated for display)* |\n");
                }
            } else {
                inTable = false;
                dataRowCount = 0;
                result
                        .append(line)
                        .append('\n');
            }
        }

        return result.toString();
    }

    /**
     * Stores a DEBUG_DETAIL message in the chat history.
     */
    private void storeDebugDetail(final int chatId, final String debugDetail) {
        try {
            aiService.storeMessage(chatId, AiMessageType.DEBUG_DETAIL, debugDetail);
        } catch (final Exception e) {
            LOGGER.warn(() -> "Failed to store debug detail for chatId=" + chatId, e);
        }
    }

    /**
     * Checks if the exception (or its cause chain) is a socket timeout error,
     * indicating the AI model took too long to respond.
     */
    private boolean isTimeoutError(final Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Estimates the total token count for a list of chat messages using a simple
     * character-based approximation (~4 characters per token).
     */
    private int estimateTokenCount(final List<ChatMessage> messages) {
        int totalChars = 0;
        for (final ChatMessage message : messages) {
            totalChars += message.toString().length();
        }
        return totalChars / 4;
    }

    /**
     * Waits for any DOWNLOADING attachments to become READY (or ERROR).
     * Polls with a configurable interval and respects the overall timeout.
     * Updates the existing WORKING message in place to give the user progress feedback.
     */
    private void waitForAttachments(final int chatId, final int workingMessageId) {
        final long timeoutMs = NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAiConfig::getAttachmentDownloadTimeoutMs,
                AskStroomAiConfig.DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS);
        final long deadline = System.currentTimeMillis() + timeoutMs;

        LOGGER.debug(() -> "waitForAttachments: chatId=" + chatId + " timeoutMs=" + timeoutMs);

        final AtomicBoolean cancelled = runManager.stopFlag(chatId);

        while (System.currentTimeMillis() < deadline) {
            // Respect cancellation — exit immediately if the user has cancelled.
            if (cancelled != null && cancelled.get()) {
                return;
            }

            final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);
            final boolean anyDownloading = attachments.stream()
                    .anyMatch(a -> a.getStatus() == AiAttachmentStatus.PENDING
                                   || a.getStatus() == AiAttachmentStatus.DOWNLOADING);

            if (!anyDownloading) {
                LOGGER.debug(() -> "waitForAttachments: chatId=" + chatId + " all attachments ready");
                return;
            }

            LOGGER.trace(() -> "waitForAttachments: chatId=" + chatId
                               + " still waiting, statuses=" + attachments.stream()
                                       .map(a -> a.getId() + ":" + a.getStatus())
                                       .collect(java.util.stream.Collectors.joining(",")));

            aiService.updateMessageText(workingMessageId,
                    "Waiting for table data download...");

            try {
                Thread.sleep(ATTACHMENT_POLL_INTERVAL_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for attachments", e);
            }
        }

        // Timed out — log a warning but continue (the question will use whatever is READY).
        LOGGER.warn(() -> "Timed out waiting for attachment downloads for chat " + chatId);
        aiService.updateMessageText(workingMessageId,
                "Attachment download timed out, proceeding with available data");
    }

    /**
     * Builds a textual summary of the recent conversation history for inclusion in
     * LLM prompts. This allows the AI to be aware of prior questions and answers.
     */
    private String buildConversationSummary(final int chatId) {
        final List<AiChatMessage> history = aiService.getMessages(chatId);
        final List<AiChatMessage> relevantMessages = history.stream()
                .filter(m -> m.getMessageType() == AiMessageType.USER_MESSAGE
                             || m.getMessageType() == AiMessageType.AI_RESPONSE)
                .toList();

        if (relevantMessages.isEmpty()) {
            return "";
        }

        // Take last N messages for context.
        final int maxHistory = NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAiConfig::getMaxHistorySafetyCapMessages,
                AskStroomAiConfig.DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES);
        final int startIdx = Math.max(0, relevantMessages.size() - maxHistory);

        final StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < relevantMessages.size(); i++) {
            final AiChatMessage msg = relevantMessages.get(i);
            if (msg.getMessageType() == AiMessageType.USER_MESSAGE) {
                sb
                        .append("User: ")
                        .append(msg.getMessage())
                        .append('\n');
            } else {
                sb
                        .append("AI: ")
                        .append(msg.getMessage())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private OpenAIModelDoc getModelDoc(final AskStroomAiConfig config) {
        if (config == null || config.getModelRef() == null) {
            throw new RuntimeException("No model specified");
        }
        final DocRef docRef = config.getModelRef();
        if (!OpenAIModelDoc.TYPE.equals(docRef.getType())) {
            throw new RuntimeException("Default OpenAI API doc ref is incorrect");
        }

        final OpenAIModelDoc openAIModelDoc = openAIModelStore.readDocument(docRef);
        if (openAIModelDoc == null) {
            throw new RuntimeException("Default OpenAI API doc cannot be found");
        }
        return openAIModelDoc;
    }

    private ChatModel getChatModel(final OpenAIModelDoc openAIModelDoc) {
        // Note that the stub ChatModel for offline testing is applied by AiService.getChatModel().
        LOGGER.debug(() -> "getChatModel: modelId='" + openAIModelDoc.getModelId()
                           + "' docRef=" + openAIModelDoc.getUuid());
        return aiService.getChatModel(openAIModelDoc);
    }

    private TableAnalysisConfig getTableAnalysisConfig(final AskStroomAiConfig config) {
        return NullSafe.getOrElse(
                config,
                AskStroomAiConfig::getTableAnalysis,
                new TableAnalysisConfig());
    }


    /**
     * Escapes a cell value for safe inclusion in a markdown table.
     * Replaces pipe characters that would break the table structure.
     */
    private String escapeMarkdownCell(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    /**
     * Counts the number of columns in a markdown table header line.
     * Header format is: {@code | col1 | col2 | col3 |}
     */
    private int parseMarkdownColumnCount(final String headerLine) {
        if (headerLine == null || headerLine.isBlank()) {
            return 0;
        }
        // Count pipe characters and subtract 1 (there's one more pipe than columns).
        int pipeCount = 0;
        for (int i = 0; i < headerLine.length(); i++) {
            if (headerLine.charAt(i) == '|') {
                pipeCount++;
            }
        }
        // | col1 | col2 | col3 | has 4 pipes for 3 columns
        return Math.max(0, pipeCount - 1);
    }

    // ---------------------------------------------------------------------
    // Cancellation methods
    // ---------------------------------------------------------------------


    /**
     * Signals cancellation for an in-progress batch analysis.
     * The batch processing checks this flag before each batch.
     */
    public boolean cancelProcessing(final int chatId) {
        aiService.verifyOwnership(chatId);
        return runManager.stop(chatId, AiChatRunStopReason.USER);
    }


    // ---------------------------------------------------------------------
    // Chat management methods
    // ---------------------------------------------------------------------

    public AiChat createChat() {
        return aiService.createChat();
    }

    public ResultPage<AiChat> listChats(final FindAiChatHistoryCriteria criteria) {
        return aiService.listChats(criteria);
    }

    public AiChat getChat(final int chatId) {
        return aiService.getChat(chatId);
    }

    public void deleteChat(final int chatId) {
        // Get attachment IDs before cascade-deleting the chat records.
        final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);
        final List<Integer> attachmentIds = attachments.stream()
                .map(AiChatAttachment::getId)
                .toList();

        LOGGER.debug(() -> "deleteChat: chatId=" + chatId + " attachments=" + attachmentIds.size());

        aiService.deleteChat(chatId); // CASCADE deletes DB records
        mcpClientCache.evictChat(chatId);
        usageTracker.forget(chatId);
        attachmentFileStore.deleteAttachmentFiles(attachmentIds); // cleanup attachment files
    }

    public void deleteMessage(final int chatId, final int messageId) {
        aiService.verifyOwnership(chatId);

        // Look up the message to check for an associated attachment.
        final List<AiChatMessage> messages = aiService.getMessages(chatId);
        final AiChatMessage targetMessage = messages.stream()
                .filter(m -> m.getId() == messageId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Message " + messageId + " not found in chat " + chatId));

        // If this is an ATTACHMENT message, delete the attachment record + file.
        if (targetMessage.getAttachmentId() != null) {
            deleteAttachment(chatId, targetMessage.getAttachmentId());
        } else {
            // Simple message deletion.
            aiService.deleteMessage(messageId);
        }
        // The turn's digest, and any compaction covering it, no longer match it.
        chatCompactor.onTurnChanged(chatId, targetMessage.getTurnId());
    }

    public void deleteAllMessages(final int chatId) {
        aiService.verifyOwnership(chatId);

        // Get attachment IDs for filesystem cleanup.
        final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);
        final List<Integer> attachmentIds = attachments.stream()
                .map(AiChatAttachment::getId)
                .toList();

        // Delete all messages and attachments in a single transaction.
        aiService.deleteAllChatMessagesAndAttachments(chatId);

        // Clean up attachment files on disk.
        attachmentFileStore.deleteAttachmentFiles(attachmentIds);
    }

    public void updateChatTitle(final int chatId, final String title) {
        aiService.updateChatTitle(chatId, title);
    }

    public List<AiChatMessage> getMessages(final int chatId) {
        return aiService.getMessages(chatId);
    }

    public AiChatPollResponse pollMessages(final int chatId, final AiChatPollRequest request) {
        aiService.verifyOwnership(chatId);
        // A browser checking on the chat means the user is still here.
        runManager.recordPresence(chatId);
        final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);

        // The WORKING message is updated in place as processing advances, so it is reported as it
        // stands rather than as one of the new messages - once the client had seen it, it would never
        // be sent again, and its progress would never be seen.
        final AiChatMessage workingMessage = aiService.getWorkingMessage(chatId).orElse(null);
        // Likewise the answer and reasoning being streamed.
        final List<AiChatMessage> newMessages = aiService
                .getMessagesSince(chatId, request.getLastSeenMessageId())
                .stream()
                .filter(msg -> !TRANSIENT_MESSAGE_TYPES.contains(msg.getMessageType()))
                .toList();

        // The conversation is complete when no run is active and all attachments have finished
        // downloading.
        AiChatRun run = runDao.getLatest(chatId).orElse(null);
        if (run != null && run.isActive() && !runDao.interruptStale(
                System.currentTimeMillis() - AiChatRunManager.STALE_AFTER.toMillis()).isEmpty()) {
            // The node working on a run has stopped, possibly this chat's.
            aiService.deleteWorkingMessages(chatId);
            run = runDao.getLatest(chatId).orElse(null);
        }
        final boolean runActive = run != null && run.isActive();
        final boolean attachmentsComplete = attachments.stream()
                .noneMatch(a -> a.getStatus() == AiAttachmentStatus.PENDING
                                || a.getStatus() == AiAttachmentStatus.DOWNLOADING);
        final boolean complete = !runActive && attachmentsComplete;
        // The context usage only changes when there are new messages or a run is working.
        final AiContextUsage contextUsage = !newMessages.isEmpty() || runActive || request.getLastSeenMessageId() == 0
                ? contextAssembler.estimateUsage(chatId)
                : null;
        // The user has now seen the chat's messages, if it is being shown.
        if (request.isMarkRead()) {
            final int lastSeen = newMessages.stream()
                    .mapToInt(AiChatMessage::getId)
                    .max()
                    .orElse(request.getLastSeenMessageId());
            aiDao.markRead(chatId, lastSeen);
        }
        final List<AiChatMessage> streamingMessages = runActive
                ? aiService.getStreamingMessages(chatId)
                : List.of();
        return new AiChatPollResponse(newMessages, attachments, workingMessage, complete, run, contextUsage,
                streamingMessages);
    }

    /**
     * @param sinceVersion The version of the statuses the client last had, or 0.
     * @return The statuses of the user's chats, or none if nothing has changed since that version.
     */
    public AiChatStatusResponse getChatStatuses(final long sinceVersion) {
        final String userUuid = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        if (userUuid == null) {
            return new AiChatStatusResponse(0L, List.of());
        }
        final long version = aiDao.getStatusVersion(userUuid);
        if (version == sinceVersion) {
            return new AiChatStatusResponse(version, null);
        }
        return new AiChatStatusResponse(version, aiDao.getChatStatuses(userUuid, MAX_CHAT_STATUSES));
    }

    // ---------------------------------------------------------------------
    // Config methods
    // ---------------------------------------------------------------------

    public AskStroomAiConfig getDefaultConfig() {
        return defaultConfigProvider.get();
    }

    public Boolean setDefaultAskStroomAIConfig(final AskStroomAiConfig config) {
        setDefaultTableAnalysisConfig(config.getTableAnalysis());

        final AskStroomAiConfig currentConfig = getDefaultConfig();
        globalConfigProvider.get().setDocRef(currentConfig,
                AskStroomAiConfig.PROP_NAME_MODEL_REF,
                config.getModelRef());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_DOCK_TYPE,
                config.getDockType().toString());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_DOCK_LOCATION,
                config.getDockLocation().toString());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_CHAT_SYSTEM_PROMPT,
                config.getChatSystemPrompt());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_HISTORY_SUMMARY_PROMPT,
                config.getHistorySummaryPrompt());
        globalConfigProvider.get().setInt(currentConfig,
                AskStroomAiConfig.PROP_NAME_MAX_HISTORY_SAFETY_CAP_MESSAGES,
                config.getMaxHistorySafetyCapMessages());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_ATTACHMENT_DOWNLOAD_TIMEOUT_MS,
                String.valueOf(config.getAttachmentDownloadTimeoutMs()));
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_ENABLE_DEBUG_DETAIL,
                String.valueOf(config.isEnableDebugDetail()));
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAiConfig.PROP_NAME_BROWSER_NOTIFICATIONS,
                String.valueOf(config.isBrowserNotifications()));
        return true;
    }

    private void setDefaultTableAnalysisConfig(final TableAnalysisConfig config) {
        final TableAnalysisConfig defaultTableAnalysisConfig = tableAnalysisConfigProvider.get();
        globalConfigProvider.get().setInt(defaultTableAnalysisConfig,
                TableAnalysisConfig.PROP_NAME_MAXIMUM_BATCH_SIZE,
                config.getMaxRowsPerBatch());
        globalConfigProvider.get().setInt(defaultTableAnalysisConfig,
                TableAnalysisConfig.PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS,
                config.getMaxTotalRows());
        globalConfigProvider.get().setInt(defaultTableAnalysisConfig,
                TableAnalysisConfig.PROP_NAME_MAX_PARALLEL_BATCHES,
                config.getMaxParallelBatches());
        globalConfigProvider.get().setString(defaultTableAnalysisConfig,
                TableAnalysisConfig.PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT,
                config.getTableQuerySystemPrompt());
        globalConfigProvider.get().setString(defaultTableAnalysisConfig,
                TableAnalysisConfig.PROP_NAME_TABLE_QUERY_USER_PROMPT,
                config.getTableQueryUserPrompt());
        globalConfigProvider.get().setString(defaultTableAnalysisConfig,
                TableAnalysisConfig.PROP_NAME_MULTI_SUMMARY_MERGE_PROMPT,
                config.getMultiSummaryMergePrompt());
    }

    // ---------------------------------------------------------------------
    // Attachment data viewing
    // ---------------------------------------------------------------------

    private static String activityLabel(final AiChatMessage msg) {
        return switch (msg.getMessageType()) {
            case THINKING -> "**AI (thinking)**";
            case TOOL_CALL -> "**AI (tool call)**";
            case TOOL_RESULT -> "**Tool result**";
            default -> "**Memory update**";
        };
    }

    /**
     * Reads the markdown attachment file for the given attachment ID,
     * parses the header row and a page of data rows, and returns them.
     */
    public AiAttachmentDataPage getAttachmentData(final GetAttachmentDataRequest request) {
        final int chatId = request.getChatId();
        final int attachmentId = request.getAttachmentId();
        aiService.verifyOwnership(chatId);

        // Verify the attachment belongs to this chat.
        final AiChatAttachment attachment = aiService.getAttachment(attachmentId)
                .orElseThrow(() -> new RuntimeException(
                        "Attachment " + attachmentId + " not found"));
        if (attachment.getChatId() != chatId) {
            throw new RuntimeException(
                    "Attachment " + attachmentId + " does not belong to chat " + chatId);
        }

        final Path mdFile = workspaceStore.localFile(attachmentId);
        if (!Files.exists(mdFile)) {
            return new AiAttachmentDataPage(
                    Collections.emptyList(), Collections.emptyList(), 0, 0);
        }

        final int offset = NullSafe.getOrElse(
                request.getPageRequest(), PageRequest::getOffset, 0);
        final int length = NullSafe.getOrElse(
                request.getPageRequest(), PageRequest::getLength, PageRequest.DEFAULT_PAGE_LENGTH);

        try (final BufferedReader reader = Files.newBufferedReader(mdFile, StandardCharsets.UTF_8)) {
            // Line 1: header row  "| Col1 | Col2 | Col3 |"
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                return new AiAttachmentDataPage(
                        Collections.emptyList(), Collections.emptyList(), 0, 0);
            }
            final List<String> headers = parseMarkdownRow(headerLine);

            // Line 2: separator row "| --- | --- | --- |"
            reader.readLine();

            // Read through data rows, counting total and collecting the requested page.
            final List<List<String>> pageRows = new ArrayList<>();
            int totalRowCount = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    if (totalRowCount >= offset && totalRowCount < offset + length) {
                        pageRows.add(parseMarkdownRow(line));
                    }
                    totalRowCount++;
                }
            }

            return new AiAttachmentDataPage(headers, pageRows, totalRowCount, offset);
        } catch (final IOException e) {
            throw new UncheckedIOException(
                    "Failed to read attachment file for ID " + attachmentId, e);
        }
    }

    /**
     * Parse a single markdown table row into a list of cell values.
     * Input: "| val1 | val2 | val3 |"
     * Output: ["val1", "val2", "val3"]
     */
    private List<String> parseMarkdownRow(final String line) {
        final List<String> cells = new ArrayList<>();
        // Split on unescaped pipe characters.
        final String[] parts = line.split("(?<!\\\\)\\|", -1);
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // Unescape any escaped pipes.
                cells.add(trimmed.replace("\\|", "|"));
            }
        }
        return cells;
    }

    // ---------------------------------------------------------------------
    // Chat history download
    // ---------------------------------------------------------------------

    public ResourceGeneration downloadChatHistory(final DownloadChatHistoryRequest request) {
        final int chatId = request.getChatId();
        aiService.verifyOwnership(chatId);

        final boolean includeDataContexts = request.isIncludeDataContexts();
        final boolean includeActivity = request.isIncludeActivity();

        final AiChat chat = aiService.getChat(chatId);
        final List<AiChatMessage> messages = aiService.getMessages(chatId);

        LOGGER.debug(() -> "downloadChatHistory: chatId=" + chatId
                           + " includeDataContexts=" + includeDataContexts
                           + " messageCount=" + messages.size());

        final String title = chat != null && chat.getTitle() != null
                ? chat.getTitle()
                : "chat-" + chatId;
        final String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        final DateTimeFormatter msgFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC);

        final String filename = sanitiseFilename(title) + ".md";
        final ResourceKey key = resourceStore.createTempFile(filename);
        final Path file = resourceStore.getTempFile(key);
        try (final BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# ");
            writer.write(title);
            writer.write("\n\n_Downloaded: ");
            writer.write(timestamp);
            writer.write("_\n\n---\n\n");

            for (final AiChatMessage msg : messages) {
                final String label;
                switch (msg.getMessageType()) {
                    case USER_MESSAGE:
                        label = "**You**";
                        break;
                    case STEER_MESSAGE:
                        label = "**You (while it was working)**";
                        break;
                    case AI_RESPONSE:
                        label = "**AI**";
                        break;
                    case ERROR:
                        label = "**Error**";
                        break;
                    case WARNING:
                        label = "**Warning**";
                        break;
                    case THINKING:
                    case TOOL_CALL:
                    case TOOL_RESULT:
                    case MEMORY_UPDATE:
                        if (!includeActivity) {
                            continue;
                        }
                        label = activityLabel(msg);
                        break;
                    case ATTACHMENT:
                        if (!includeDataContexts || msg.getAttachmentId() == null) {
                            continue;
                        }
                        label = "**Data Context**";
                        break;
                    default:
                        // WORKING, DASHBOARD_DATA, QUERY_DATA, TABLE_DATA, DEBUG_DETAIL and the rest are
                        // always omitted (the actual table data lives in the attachment file store; debug
                        // detail is only for in-UI inspection).
                        continue;
                }

                final String ts = msgFmt.format(Instant.ofEpochMilli(msg.getCreateTimeMs()));
                writer.write(label);
                writer.write(" _(");
                writer.write(ts);
                writer.write(")_\n\n");

                if (msg.getMessageType() == AiMessageType.ATTACHMENT) {
                    // Stream the markdown from the attachment file store as a Markdown table.
                    writeAttachmentAsMarkdownTable(writer, msg.getAttachmentId(), msg.getMessage());
                } else {
                    final String body = AiActivityExport.isActivity(msg.getMessageType())
                            ? AiActivityExport.body(msg)
                            : NullSafe.string(msg.getMessage());
                    writer.write(body);
                    writer.newLine();
                }
                writer.newLine();
                writer.write("---\n\n");
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.debug(() -> {
            try {
                return "downloadChatHistory: wrote " + filename
                       + " (" + Files.size(file) + " bytes)";
            } catch (final IOException e) {
                return "downloadChatHistory: wrote " + filename;
            }
        });
        return new ResourceGeneration(key, new ArrayList<>());
    }

    /**
     * Reads the markdown attachment file for the given attachment ID and writes it as a
     * Markdown table directly to the supplied writer. If the file is not found or
     * cannot be read, a fallback description line is written instead.
     */
    private void writeAttachmentAsMarkdownTable(final BufferedWriter writer,
                                                final int attachmentId,
                                                final String description) throws IOException {
        final Path mdFile = workspaceStore.localFile(attachmentId);
        if (!Files.exists(mdFile)) {
            LOGGER.debug(() -> "writeAttachmentAsMarkdownTable: file not found for attachmentId="
                               + attachmentId + " (may have been cleaned up)");
            writer.write("_Data not available (attachment ");
            writer.write(String.valueOf(attachmentId));
            writer.write(" may have been cleaned up)_\n");
            return;
        }
        if (description != null && !description.isBlank()) {
            writer.write("_");
            writer.write(description);
            writer.write("_\n\n");
        }
        try (final BufferedReader reader = Files.newBufferedReader(mdFile, StandardCharsets.UTF_8)) {
            // File is already in markdown format — stream it directly.
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static String sanitiseFilename(final String name) {
        if (name == null || name.isBlank()) {
            return "chat-history";
        }
        // Replace characters that are illegal in common filesystems with underscores.
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_").replaceAll("_+", "_");
    }
}
