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
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.config.global.api.GlobalConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.OffsetRange;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class AskStroomAIService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAIService.class);


    /**
     * Sentinel model ID that activates the stub ChatModel for offline testing.
     * Create an OpenAIModel document with this as the modelId — no API key or base URL needed.
     */
    static final String STUB_MODEL_ID = "__stub__";

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
    private final Provider<AskStroomAIConfig> defaultConfigProvider;
    private final Provider<GlobalConfig> globalConfigProvider;
    private final Provider<TableAnalysisConfig> tableAnalysisConfigProvider;
    private final ConcurrentHashMap<Integer, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    private final TaskContextFactory taskContextFactory;

    @Inject
    public AskStroomAIService(final AiService aiService,
                              final AiAttachmentFileStore attachmentFileStore,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore,
                              final ResourceStore resourceStore,
                              final ExecutorProvider executorProvider,
                              final Provider<AskStroomAIConfig> defaultConfigProvider,
                              final Provider<GlobalConfig> globalConfigProvider,
                              final Provider<TableAnalysisConfig> tableAnalysisConfigProvider,
                              final TaskContextFactory taskContextFactory) {
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

        // Stage 2: If a user message is present, process the question.
        if (request.getMessage() != null) {
            aiService.storeMessage(chatId, AiMessageType.USER_MESSAGE, request.getMessage());

            String responseText;
            try {
                responseText = processQuestion(request);

                if (responseText != null) {
                    aiService.storeMessage(chatId, AiMessageType.AI_RESPONSE, responseText);
                    final String rt = responseText;
                    LOGGER.debug(() -> "Response stored for chatId=" + chatId
                                       + " responseLength=" + rt.length());
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                responseText = e.getMessage();
                aiService.storeMessage(chatId, AiMessageType.ERROR, responseText);
            }

            return new AskStroomAiResponse(responseText);
        }

        // Context-only request (attachment created, no question yet).
        return new AskStroomAiResponse(null);
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
                                  final AskStroomAIConfig config) {
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
            processGeneralAttachment(attachment.getId(), generalTableContext);
        } else {
            // Async download for Dashboard/Query contexts.
            submitAsyncDownload(attachment.getId(), chatId, context, config);
        }
    }

    /**
     * Converts GeneralTableContext (in-memory table data) directly to a markdown file
     * in the attachment file store and marks the attachment as READY.
     */
    private void processGeneralAttachment(final int attachmentId,
                                          final GeneralTableContext generalTableContext) {
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
                            .map(_ -> "| --- ")
                            .collect(Collectors.joining()));
                    writer.write("|\n");
                    // Write rows
                    for (final List<String> rowValues : generalTableContext.getRows()) {
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

            final String description = generalTableContext.getDescription()
                                       + " (" + rowCount + " rows, "
                                       + generalTableContext.getColumns().size() + " cols)";
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.READY,
                    rowCount, description, null, false);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.ERROR,
                    null, null, e.getMessage(), false);
            attachmentFileStore.deleteAttachmentFile(attachmentId);
        }
    }

    /**
     * Submits an async task to download table data from a Dashboard or Query search
     * and save the markdown to the attachment file store.
     */
    private void submitAsyncDownload(final int attachmentId,
                                     final int chatId,
                                     final AskStroomAiContext context,
                                     final AskStroomAIConfig config) {
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
     * Core question processing. Creates a single THINKING message that is updated in place
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

        final AskStroomAIConfig config = request.getConfig();
        final ChatModel chatModel = getChatModel(config);

        // Create a single THINKING message that will be updated in place.
        final AiChatMessage thinkingMsg = aiService.storeMessage(
                chatId, AiMessageType.THINKING, "Thinking...");
        final int thinkingMessageId = thinkingMsg.getId();

        try {
            // Wait for any DOWNLOADING attachments to become READY.
            LOGGER.logDurationIfDebugEnabled(
                    () -> waitForAttachments(chatId, thinkingMessageId),
                    () -> "waitForAttachments chatId=" + chatId);

            // Check for READY attachments.
            final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);
            final List<AiChatAttachment> readyAttachments = attachments.stream()
                    .filter(a -> a.getStatus() == AiAttachmentStatus.READY)
                    .toList();

            LOGGER.debug(() -> "processQuestion: chatId=" + chatId
                               + " readyAttachments=" + readyAttachments.size());

            // Build the full relevant history (filtered and capped by safety limit).
            final Map<Integer, AiChatAttachment> attachmentMap = readyAttachments.stream()
                    .collect(Collectors.toMap(AiChatAttachment::getId, a -> a));
            final List<AiChatMessage> history = aiService.getMessages(chatId);
            final List<AiChatMessage> relevantHistory = history.stream()
                    .filter(m -> m.getMessageType() == AiMessageType.USER_MESSAGE
                                 || m.getMessageType() == AiMessageType.AI_RESPONSE
                                 || m.getMessageType() == AiMessageType.ERROR
                                 || m.getMessageType() == AiMessageType.ATTACHMENT)
                    .toList();

            final int safetyCap = NullSafe.getOrElse(
                    defaultConfigProvider.get(),
                    AskStroomAIConfig::getMaxHistorySafetyCapMessages,
                    AskStroomAIConfig.DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES);
            int maxHistory = Math.min(safetyCap, relevantHistory.size());
            String contextSummary = null;
            boolean wasTrimmed = false;

            // Progressive-trim retry loop.
            for (int attempt = 1; ; attempt++) {
                final int currentAttempt = attempt;
                final int currentMaxHistory = maxHistory;
                final String currentSummary = contextSummary;

                final List<ChatMessage> messages = buildUnifiedMessages(
                        request, chatId, attachmentMap, relevantHistory,
                        currentMaxHistory, currentSummary);

                LOGGER.debug(() -> "processQuestion: attempt " + currentAttempt
                                   + " maxHistory=" + currentMaxHistory
                                   + " messages=" + messages.size()
                                   + " hasSummary=" + (currentSummary != null)
                                   + " chatId=" + chatId);

                try {
                    final String responseText = processUnified(
                            chatModel, chatId, messages, currentAttempt);

                    // If we trimmed, append a user-visible note.
                    if (wasTrimmed) {
                        return responseText
                               + "\n\n---\n*Note: Some earlier conversation history was "
                               + "summarised to fit the model's context window. "
                               + "The most recent messages and attachments were preserved.*";
                    }
                    return responseText;

                } catch (final Exception e) {
                    if (!isContextOverflowError(e)) {
                        throw e;
                    }

                    // Can we trim further?
                    if (maxHistory <= 0) {
                        // Nothing left to trim — fall back to batch if there are attachments.
                        if (!readyAttachments.isEmpty()) {
                            LOGGER.info(() -> "Context overflow with no history remaining for "
                                              + "chatId=" + chatId
                                              + ", falling back to batch processing");
                            final String result = analyseWithAttachments(request, chatModel,
                                    readyAttachments, chatId, thinkingMessageId);
                            return result + "\n\n---\n*Note: The attached data was too large "
                                   + "for full analysis in a single call. Results were produced "
                                   + "using batch processing and may not capture cross-row "
                                   + "patterns or cross-table comparisons as effectively.*";
                        }
                        // No attachments and still overflowing — nothing we can do.
                        throw e;
                    }

                    // Summarise the messages being dropped before trimming.
                    wasTrimmed = true;
                    final int newMaxHistory = Math.max(0, maxHistory - 2);
                    final int oldStartIdx = Math.max(0,
                            relevantHistory.size() - maxHistory - 1);
                    final int newStartIdx = Math.max(0,
                            relevantHistory.size() - newMaxHistory - 1);

                    // Collect the messages being dropped in this trim step.
                    final List<AiChatMessage> droppedMessages = new ArrayList<>();
                    for (int i = oldStartIdx; i < newStartIdx
                                              && i < relevantHistory.size() - 1; i++) {
                        droppedMessages.add(relevantHistory.get(i));
                    }

                    if (!droppedMessages.isEmpty()) {
                        LOGGER.debug(() -> "processQuestion: trimming " + droppedMessages.size()
                                           + " messages, summarising for chatId=" + chatId);
                        try {
                            contextSummary = summariseDroppedMessages(
                                    chatModel, contextSummary, droppedMessages, config);
                        } catch (final Exception sumEx) {
                            LOGGER.warn(() -> "Failed to summarise dropped messages, "
                                              + "continuing without summary", sumEx);
                            // Continue without summary — better than failing entirely.
                        }
                    }

                    maxHistory = newMaxHistory;
                    LOGGER.info(() -> "Context overflow for chatId=" + chatId
                                      + ", retrying with maxHistory=" + newMaxHistory);
                }
            }
        } finally {
            // Always clean up the THINKING message when processing is done.
            try {
                aiService.deleteMessage(thinkingMessageId);
            } catch (final Exception e) {
                LOGGER.debug(() -> "Failed to delete thinking message " + thinkingMessageId, e);
            }
        }
    }

    /**
     * Builds the list of ChatMessages for a unified LLM call. Includes system prompt,
     * optional context summary (from prior trimming), filtered conversation history
     * with inline attachment data, and the current user message.
     *
     * @param maxHistory     maximum number of history messages to include (from the end)
     * @param contextSummary optional summary of earlier messages that were trimmed
     */
    private List<ChatMessage> buildUnifiedMessages(final AskStroomAiRequest request,
                                                   final int chatId,
                                                   final Map<Integer, AiChatAttachment> attachmentMap,
                                                   final List<AiChatMessage> relevantHistory,
                                                   final int maxHistory,
                                                   final String contextSummary) {
        final List<ChatMessage> messages = new ArrayList<>();

        // System message.
        messages.add(new SystemMessage(NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAIConfig::getChatSystemPrompt,
                AskStroomAIConfig.DEFAULT_CHAT_SYSTEM_PROMPT)));

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
                        final Path mdFile = attachmentFileStore.getAttachmentFile(attachment.getId());
                        if (Files.exists(mdFile)) {
                            try {
                                final String markdown = Files.readString(mdFile, StandardCharsets.UTF_8);
                                final String description = NullSafe.getOrElse(
                                        attachment, AiChatAttachment::getDescription,
                                        "Attachment " + attachment.getId());
                                final StringBuilder tagged = new StringBuilder();
                                tagged.append("[Attached Table: ").append(description);
                                if (attachment.getRowCount() != null) {
                                    tagged.append(" (").append(attachment.getRowCount()).append(" rows)");
                                }
                                if (attachment.isTruncated()) {
                                    tagged.append(" TRUNCATED");
                                }
                                tagged.append("]\n").append(markdown);
                                messages.add(new UserMessage(tagged.toString()));
                            } catch (final IOException e) {
                                LOGGER.warn(() -> "Failed to read attachment file: " + mdFile, e);
                            }
                        }
                    }
                }
                case USER_MESSAGE -> messages.add(new UserMessage(msg.getMessage()));
                default -> messages.add(new AiMessage(msg.getMessage()));
            }
        }

        // Add the current user message.
        messages.add(new UserMessage(request.getMessage()));

        return messages;
    }

    /**
     * Sends the pre-built message list to the LLM and returns the response text.
     */
    private String processUnified(final ChatModel chatModel,
                                  final int chatId,
                                  final List<ChatMessage> messages,
                                  final int attempt) {
        LOGGER.debug(() -> "processUnified: sending " + messages.size()
                           + " messages to LLM for chatId=" + chatId
                           + " (attempt " + attempt + ")");

        final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                () -> chatModel.chat(messages),
                r -> "processUnified chatId=" + chatId
                     + " attempt=" + attempt
                     + " responseLength=" + r.aiMessage().text().length());
        LOGGER.trace(() -> "processUnified response:\n" + response.aiMessage().text());
        return response.aiMessage().text();
    }

    /**
     * Summarises dropped conversation messages so that context is preserved
     * even after progressive trimming. If a previous summary exists, it is
     * included so that context accumulates across trim iterations.
     *
     * @param existingSummary previous summary from earlier trim iterations (may be null)
     * @param droppedMessages the messages being dropped in this trim step
     * @return a concise summary string
     */
    private String summariseDroppedMessages(final ChatModel chatModel,
                                            final String existingSummary,
                                            final List<AiChatMessage> droppedMessages,
                                            final AskStroomAIConfig config) {
        final StringBuilder input = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            input.append("Previous context summary:\n").append(existingSummary).append("\n\n");
        }
        input.append("Additional conversation to summarise:\n");
        for (final AiChatMessage msg : droppedMessages) {
            switch (msg.getMessageType()) {
                case USER_MESSAGE -> input.append("User: ").append(msg.getMessage()).append('\n');
                case AI_RESPONSE -> input.append("Assistant: ").append(msg.getMessage()).append('\n');
                case ATTACHMENT -> input.append("User attached table: ")
                        .append(msg.getMessage()).append('\n');
                default -> input.append("System: ").append(msg.getMessage()).append('\n');
            }
        }

        final String systemPrompt = NullSafe.getOrElse(
                config,
                AskStroomAIConfig::getHistorySummaryPrompt,
                AskStroomAIConfig.DEFAULT_HISTORY_SUMMARY_PROMPT);

        final List<ChatMessage> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(input.toString()));

        final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                () -> chatModel.chat(messages),
                r -> "summariseDroppedMessages: responseLength="
                     + r.aiMessage().text().length());
        LOGGER.trace(() -> "summariseDroppedMessages response:\n" + response.aiMessage().text());
        return response.aiMessage().text();
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
                                          final int thinkingMessageId) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        registerCancellation(chatId, cancelled);

        try {
            final TableAnalysisConfig tableAnalysisConfig = getTableAnalysisConfig(request.getConfig());
            final String conversationContext = buildConversationSummary(chatId);

            LOGGER.debug(() -> "analyseWithAttachments: chatId=" + chatId
                               + " attachments=" + attachments.size()
                               + " maxParallel=" + tableAnalysisConfig.getMaxParallelBatches());

            // Build batches from markdown files on disk.
            final List<String> batches = new ArrayList<>();
            boolean anyTruncated = false;
            for (final AiChatAttachment attachment : attachments) {
                final Path mdFile = attachmentFileStore.getAttachmentFile(attachment.getId());
                if (!Files.exists(mdFile)) {
                    throw new RuntimeException(
                            "Attachment data file not found for attachment " + attachment.getId()
                            + ". Data may have been cleaned up.");
                }
                batches.addAll(buildBatchesFromMarkdown(mdFile, tableAnalysisConfig));
                if (attachment.isTruncated()) {
                    anyTruncated = true;
                }
            }

            if (batches.isEmpty()) {
                return "No data available for analysis.";
            }

            final boolean truncatedData = anyTruncated;
            LOGGER.debug(() -> "analyseWithAttachments: chatId=" + chatId
                               + " batches=" + batches.size() + " anyTruncated=" + truncatedData);

            // Include truncation note in the user query if applicable.
            final String userQuery = anyTruncated
                    ? request.getMessage() + "\n\nNote: this data is truncated to the first "
                      + tableAnalysisConfig.getMaxTotalRows()
                      + " rows of a larger result set."
                    : request.getMessage();

            final int totalBatches = batches.size();
            aiService.updateMessageText(thinkingMessageId,
                    "Analysing " + totalBatches + " batch(es) across "
                    + attachments.size() + " attachment(s)...");

            // Process batches in parallel with bounded concurrency.
            final Executor executor = executorProvider.get();
            final int maxParallel = tableAnalysisConfig.getMaxParallelBatches();
            final Semaphore semaphore = new Semaphore(maxParallel);

            final String systemPrompt = tableAnalysisConfig.getTableQuerySystemPrompt() != null
                    ? tableAnalysisConfig.getTableQuerySystemPrompt()
                    : TableAnalysisConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
            final String userPromptTemplate = tableAnalysisConfig.getTableQueryUserPrompt() != null
                    ? tableAnalysisConfig.getTableQueryUserPrompt()
                    : TableAnalysisConfig.DEFAULT_TABLE_QUERY_USER_PROMPT;

            final List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < totalBatches; i++) {
                if (cancelled.get()) {
                    final int batchIdx = i;
                    LOGGER.debug(() -> "analyseWithAttachments: cancelled for chatId=" + chatId
                                       + " at batch " + batchIdx + "/" + totalBatches);
                    break;
                }
                final String batch = batches.get(i);
                final int batchNum = i + 1;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted waiting for batch slot", e);
                    }
                    try {
                        if (cancelled.get()) {
                            return null;
                        }
                        aiService.updateMessageText(thinkingMessageId,
                                "Analysing batch " + batchNum + " of " + totalBatches + "...");

                        final String userPrompt = userPromptTemplate
                                .replace("{{query}}", userQuery)
                                .replace("{{table}}", batch)
                                .replace("{{context}}", conversationContext);

                        LOGGER.trace(() -> "Batch " + batchNum + "/" + totalBatches
                                           + " prompt (chatId=" + chatId + "):\n" + userPrompt);

                        final List<ChatMessage> messages = List.of(
                                new SystemMessage(systemPrompt),
                                new UserMessage(userPrompt));

                        final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                                () -> chatModel.chat(messages),
                                r -> "Batch " + batchNum + "/" + totalBatches
                                     + " chatId=" + chatId
                                     + " responseLength=" + r.aiMessage().text().length());
                        LOGGER.trace(() -> "Batch " + batchNum + "/" + totalBatches
                                           + " response:\n" + response.aiMessage().text());
                        return response.aiMessage().text();
                    } finally {
                        semaphore.release();
                    }
                }, executor));
            }

            // Collect results, handling per-batch failures gracefully.
            final List<String> summaries = new ArrayList<>();
            for (final CompletableFuture<String> future : futures) {
                try {
                    final String result = future.join();
                    if (result != null && !result.isEmpty()) {
                        summaries.add(result);
                    }
                } catch (final Exception e) {
                    LOGGER.debug(() -> "Batch processing failed", e);
                    // Continue collecting results from other batches.
                }
            }

            if (summaries.isEmpty()) {
                if (cancelled.get()) {
                    return "Analysis was cancelled before any results were produced.";
                }
                return "No results could be extracted from the data.";
            }

            // Merge summaries.
            LOGGER.debug(() -> "analyseWithAttachments: chatId=" + chatId
                               + " summaries=" + summaries.size()
                               + (summaries.size() > 1
                    ? " -> merging"
                    : " -> single result"));
            if (summaries.size() == 1) {
                return summaries.getFirst();
            }
            return mergeAllSummaries(chatModel, summaries, tableAnalysisConfig);
        } finally {
            deregisterCancellation(chatId);
        }
    }

    /**
     * Reads a markdown table file and splits it into batches,
     * each respecting the maximum batch size. The header and separator
     * rows are preserved at the start of each batch.
     */
    List<String> buildBatchesFromMarkdown(final Path mdFile,
                                          final TableAnalysisConfig config) {
        return LOGGER.logDurationIfDebugEnabled(() -> {
            final List<String> batches = new ArrayList<>();
            final int maxBatchSize = config.getMaxRowsPerBatch();

            try (final BufferedReader reader = Files.newBufferedReader(mdFile)) {
                final String headerLine = reader.readLine();
                if (headerLine == null) {
                    return batches;
                }
                final String separatorLine = reader.readLine();
                if (separatorLine == null) {
                    return batches;
                }

                final String mdHeader = headerLine + "\n" + separatorLine + "\n";
                final StringBuilder batch = new StringBuilder(mdHeader);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    final String row = line + "\n";
                    if (batch.length() + row.length() > maxBatchSize
                        && batch.length() > mdHeader.length()) {
                        batches.add(batch.toString());
                        batch.setLength(0);
                        batch.append(mdHeader);
                    }
                    batch.append(row);
                }

                if (batch.length() > mdHeader.length()) {
                    batches.add(batch.toString());
                }
            } catch (final IOException e) {
                throw new UncheckedIOException("Failed to read markdown file: " + mdFile, e);
            }
            return batches;
        }, batches -> "buildBatchesFromMarkdown: file=" + mdFile.getFileName()
                      + ", maxRowsPerBatch=" + config.getMaxRowsPerBatch()
                      + ", batch(es)=" + batches.size());
    }

    /**
     * Merges N summaries into a single unified summary using a single LLM call.
     */
    private String mergeAllSummaries(final ChatModel chatModel,
                                     final List<String> summaries,
                                     final TableAnalysisConfig config) {
        LOGGER.debug(() -> "mergeAllSummaries: merging " + summaries.size() + " summaries");

        final StringBuilder combined = new StringBuilder();
        for (int i = 0; i < summaries.size(); i++) {
            combined.append("--- Summary ").append(i + 1).append(" ---\n");
            combined.append(summaries.get(i)).append("\n\n");
        }

        final String mergePromptTemplate = config.getMultiSummaryMergePrompt() != null
                ? config.getMultiSummaryMergePrompt()
                : TableAnalysisConfig.DEFAULT_MULTI_SUMMARY_MERGE_PROMPT;
        final String mergePrompt = mergePromptTemplate
                .replace("{{summaries}}", combined.toString());

        LOGGER.trace(() -> "mergeAllSummaries prompt:\n" + mergePrompt);

        final List<ChatMessage> messages = List.of(
                new SystemMessage("You merge partial answers into a unified, concise summary."),
                new UserMessage(mergePrompt));

        final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                () -> chatModel.chat(messages),
                r -> "mergeAllSummaries: responseLength=" + r.aiMessage().text().length());
        LOGGER.trace(() -> "mergeAllSummaries response:\n" + response.aiMessage().text());
        return response.aiMessage().text();
    }

    /**
     * Checks whether an exception represents a context window overflow error
     * from the LLM API. These typically come as HTTP 400 errors with messages
     * about token limits being exceeded.
     */
    private boolean isContextOverflowError(final Exception e) {
        final String message = e.getMessage();
        if (message == null) {
            return false;
        }
        final String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("maximum context length")
               || lowerMessage.contains("token limit")
               || lowerMessage.contains("too many tokens")
               || lowerMessage.contains("context_length_exceeded")
               || lowerMessage.contains("max_tokens")
               || lowerMessage.contains("context window")
               || lowerMessage.contains("input is too long")
               || lowerMessage.contains("request too large");
    }

    /**
     * Waits for any DOWNLOADING attachments to become READY (or ERROR).
     * Polls with a configurable interval and respects the overall timeout.
     * Updates the existing THINKING message in place to give the user progress feedback.
     */
    private void waitForAttachments(final int chatId, final int thinkingMessageId) {
        final long timeoutMs = NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAIConfig::getAttachmentDownloadTimeoutMs,
                AskStroomAIConfig.DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS);
        final long deadline = System.currentTimeMillis() + timeoutMs;

        LOGGER.debug(() -> "waitForAttachments: chatId=" + chatId + " timeoutMs=" + timeoutMs);

        // Look up the cancellation flag (may be null if not yet registered for this chat).
        final AtomicBoolean cancelled = cancellationFlags.get(chatId);

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

            aiService.updateMessageText(thinkingMessageId,
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
        aiService.updateMessageText(thinkingMessageId,
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
                AskStroomAIConfig::getMaxHistorySafetyCapMessages,
                AskStroomAIConfig.DEFAULT_MAX_HISTORY_SAFETY_CAP_MESSAGES);
        final int startIdx = Math.max(0, relevantMessages.size() - maxHistory);

        final StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < relevantMessages.size(); i++) {
            final AiChatMessage msg = relevantMessages.get(i);
            if (msg.getMessageType() == AiMessageType.USER_MESSAGE) {
                sb.append("User: ").append(msg.getMessage()).append('\n');
            } else {
                sb.append("AI: ").append(msg.getMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    private ChatModel getChatModel(final AskStroomAIConfig config) {
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

        // Stub mode: return a test ChatModel that requires no API key or network.
        if (STUB_MODEL_ID.equals(openAIModelDoc.getModelId())) {
            LOGGER.info(() -> "Using stub ChatModel for testing (modelId='" + STUB_MODEL_ID + "')");
            return new StubChatModel();
        }

        LOGGER.debug(() -> "getChatModel: modelId='" + openAIModelDoc.getModelId()
                           + "' docRef=" + docRef.getUuid());
        return aiService.getChatModel(openAIModelDoc);
    }

    private TableAnalysisConfig getTableAnalysisConfig(final AskStroomAIConfig config) {
        return NullSafe.getOrElse(
                config,
                AskStroomAIConfig::getTableAnalysis,
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

    void registerCancellation(final int chatId, final AtomicBoolean flag) {
        cancellationFlags.put(chatId, flag);
    }

    /**
     * Signals cancellation for an in-progress batch analysis.
     * The batch processing checks this flag before each batch.
     */
    public boolean cancelProcessing(final int chatId) {
        aiService.verifyOwnership(chatId);
        final AtomicBoolean flag = cancellationFlags.get(chatId);
        LOGGER.debug(() -> "cancelProcessing: chatId=" + chatId + " flagFound=" + (flag != null));
        if (flag != null) {
            flag.set(true);
            return true;
        }
        return false;
    }

    void deregisterCancellation(final int chatId) {
        cancellationFlags.remove(chatId);
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
        attachmentFileStore.deleteAttachmentFiles(attachmentIds); // cleanup attachment files
    }

    public void updateChatTitle(final int chatId, final String title) {
        aiService.updateChatTitle(chatId, title);
    }

    public List<AiChatMessage> getMessages(final int chatId) {
        return aiService.getMessages(chatId);
    }

    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final String message) {
        return aiService.storeMessage(chatId, messageType, message);
    }

    public AiChatPollResponse pollMessages(final int chatId, final AiChatPollRequest request) {
        final List<AiChatMessage> newMessages = aiService.getMessagesSince(
                chatId, request.getLastSeenMessageId());
        final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);

        // Conversation is complete if there are no THINKING messages among the new messages
        // AND all attachments have finished downloading.
        final boolean thinkingComplete = newMessages.stream()
                .noneMatch(msg -> msg.getMessageType() == AiMessageType.THINKING);
        final boolean attachmentsComplete = attachments.stream()
                .noneMatch(a -> a.getStatus() == AiAttachmentStatus.PENDING
                                || a.getStatus() == AiAttachmentStatus.DOWNLOADING);
        final boolean complete = thinkingComplete && attachmentsComplete;
        return new AiChatPollResponse(newMessages, attachments, complete);
    }

    // ---------------------------------------------------------------------
    // Config methods
    // ---------------------------------------------------------------------

    public AskStroomAIConfig getDefaultConfig() {
        return defaultConfigProvider.get();
    }

    public Boolean setDefaultAskStroomAIConfig(final AskStroomAIConfig config) {
        setDefaultModel(config.getModelRef());
        setDefaultTableAnalysisConfig(config.getTableAnalysis());

        // Persist chat/attachment config fields.
        final AskStroomAIConfig currentConfig = getDefaultConfig();
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAIConfig.PROP_NAME_CHAT_SYSTEM_PROMPT,
                config.getChatSystemPrompt());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAIConfig.PROP_NAME_HISTORY_SUMMARY_PROMPT,
                config.getHistorySummaryPrompt());
        globalConfigProvider.get().setInt(currentConfig,
                AskStroomAIConfig.PROP_NAME_MAX_HISTORY_SAFETY_CAP_MESSAGES,
                config.getMaxHistorySafetyCapMessages());
        globalConfigProvider.get().setString(currentConfig,
                AskStroomAIConfig.PROP_NAME_ATTACHMENT_DOWNLOAD_TIMEOUT_MS,
                String.valueOf(config.getAttachmentDownloadTimeoutMs()));
        return true;
    }

    private Boolean setDefaultModel(final DocRef modelRef) {
        globalConfigProvider.get().setDocRef(getDefaultConfig(), AskStroomAIConfig.PROP_NAME_MODEL_REF, modelRef);
        return true;
    }

    private Boolean setDefaultTableAnalysisConfig(final TableAnalysisConfig config) {
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
        return true;
    }

    // ---------------------------------------------------------------------
    // Chat history download
    // ---------------------------------------------------------------------

    public ResourceGeneration downloadChatHistory(final DownloadChatHistoryRequest request) {
        final int chatId = request.getChatId();
        aiService.verifyOwnership(chatId);

        final boolean includeDataContexts = request.isIncludeDataContexts();

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
                    case AI_RESPONSE:
                        label = "**AI**";
                        break;
                    case ERROR:
                        label = "**Error**";
                        break;
                    case ATTACHMENT:
                        if (!includeDataContexts || msg.getAttachmentId() == null) {
                            continue;
                        }
                        label = "**Data Context**";
                        break;
                    default:
                        // THINKING, DASHBOARD_DATA, QUERY_DATA, TABLE_DATA are always omitted
                        // (the actual table data lives in the attachment file store).
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
                    final String body = msg.getMessage() != null
                            ? msg.getMessage()
                            : "";
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
        final Path mdFile = attachmentFileStore.getAttachmentFile(attachmentId);
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
