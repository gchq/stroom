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
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.ai.shared.DownloadChatHistoryRequest;
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
import stroom.task.api.TaskContextFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FindNamedEntityCriteria;
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
        final AiAttachmentType attachmentType;
        if (context instanceof DashboardTableContext) {
            attachmentType = AiAttachmentType.DASHBOARD;
        } else if (context instanceof QueryTableContext) {
            attachmentType = AiAttachmentType.QUERY;
        } else if (context instanceof GeneralTableContext) {
            attachmentType = AiAttachmentType.GENERAL;
        } else {
            throw new IllegalStateException("Unsupported context type: " + context.getClass().getName());
        }

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
     * Converts GeneralTableContext (in-memory table data) directly to a CSV file
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

            final Path csvFile = attachmentFileStore.createAttachmentFile(attachmentId);
            final int[] rowCountHolder = {0};
            LOGGER.logDurationIfDebugEnabled(() -> {
                try (final BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
                    // Write CSV header
                    writer.write(generalTableContext.getColumns().stream()
                            .map(this::escapeCsvField)
                            .collect(Collectors.joining(",")));
                    writer.newLine();
                    // Write rows
                    for (final List<String> rowValues : generalTableContext.getRows()) {
                        writer.write(rowValues.stream()
                                .map(this::escapeCsvField)
                                .collect(Collectors.joining(",")));
                        writer.newLine();
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
     * and save the CSV to the attachment file store.
     * Markdown conversion happens at analysis time, not here.
     */
    private void submitAsyncDownload(final int attachmentId,
                                     final int chatId,
                                     final AskStroomAiContext context,
                                     final AskStroomAIConfig config) {
        final Runnable runnable = taskContextFactory.context(
                "Download search results for AI analysis",
                taskContext -> {
                    try {
                        LOGGER.debug(() -> "Async download started: attachmentId=" + attachmentId
                                + " chatId=" + chatId
                                + " contextType=" + context.getClass().getSimpleName());
                        aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.DOWNLOADING,
                                null, null, null, false);

                        final TableAnalysisConfig tableAnalysisConfig = getTableAnalysisConfig(config);
                        final Supplier<ResourceGeneration> downloadSupplier =
                                buildDownloadSupplier(context, tableAnalysisConfig);
                        final ResourceGeneration resourceGeneration = LOGGER.logDurationIfDebugEnabled(
                                downloadSupplier::get,
                                rg -> "Download completed for attachmentId=" + attachmentId);
                        final ResourceKey resourceKey = resourceGeneration.getResourceKey();

                        try {
                            final Path tempFile = resourceStore.getTempFile(resourceKey);
                            final Path targetFile = attachmentFileStore.createAttachmentFile(attachmentId);
                            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

                            // Count rows by streaming the file (skip CSV header).
                            final int maxRows = tableAnalysisConfig.getMaxTotalRows();
                            int rowCount = 0;
                            try (final BufferedReader reader = Files.newBufferedReader(targetFile)) {
                                reader.readLine(); // skip CSV header
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
                                    while ((line = reader.readLine()) != null) {
                                        // Write header + maxRows data lines
                                        if (linesWritten <= maxRows) {
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
                                        ? parseCsvLine(header).size()
                                        : 0;
                            }

                            final String description = context.getDescription()
                                                       + " (" + reportedRowCount + " rows, " + colCount + " cols"
                                                       + (truncated
                                    ? ", truncated to limit"
                                    : "") + ")";

                            LOGGER.debug(() -> "Async download finished: attachmentId=" + attachmentId
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
                        limitedSearchRequest, componentId, DownloadSearchResultFileType.CSV,
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
                        limitedSearchRequest, DownloadSearchResultFileType.CSV, false, 100);
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
     * attachments, then routes to attachment-based batch analysis or pure conversational mode.
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
                    + " readyAttachments=" + readyAttachments.size()
                    + " \u2192 " + (readyAttachments.isEmpty() ? "conversational" : "batch analysis"));

            if (!readyAttachments.isEmpty()) {
                // Has attachments → full batch analysis with conversation context.
                return analyseWithAttachments(request, chatModel, readyAttachments,
                        chatId, thinkingMessageId);
            } else {
                // No attachments → pure conversational mode.
                return processConversational(request, chatModel, chatId);
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
     * Performs parallel batch analysis over all READY attachments, reading CSV data
     * from the attachment file store. Each batch is converted to markdown on-the-fly
     * and submitted to the LLM concurrently. Results are merged in a single pass.
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

            // Build batches from CSV files on disk.
            final List<String> batches = new ArrayList<>();
            boolean anyTruncated = false;
            for (final AiChatAttachment attachment : attachments) {
                final Path csvFile = attachmentFileStore.getAttachmentFile(attachment.getId());
                if (!Files.exists(csvFile)) {
                    throw new RuntimeException(
                            "Attachment data file not found for attachment " + attachment.getId()
                            + ". Data may have been cleaned up.");
                }
                batches.addAll(buildBatchesFromCsv(csvFile, tableAnalysisConfig));
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
                    + (summaries.size() > 1 ? " \u2192 merging" : " \u2192 single result"));
            if (summaries.size() == 1) {
                return summaries.getFirst();
            }
            return mergeAllSummaries(chatModel, summaries, tableAnalysisConfig);
        } finally {
            deregisterCancellation(chatId);
        }
    }

    /**
     * Reads a CSV file and splits it into markdown-formatted batches,
     * each respecting the maximum batch size.
     */
    List<String> buildBatchesFromCsv(final Path csvFile,
                                     final TableAnalysisConfig config) {
        return LOGGER.logDurationIfDebugEnabled(() -> {
            final List<String> batches = new ArrayList<>();
            final int maxBatchSize = config.getMaxRowsPerBatch();

            try (final BufferedReader reader = Files.newBufferedReader(csvFile)) {
                final String csvHeader = reader.readLine();
                if (csvHeader == null) {
                    return batches;
                }

                final String mdHeader = buildMarkdownHeader(csvHeader);
                final StringBuilder batch = new StringBuilder(mdHeader);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    final String mdRow = csvLineToMarkdownRow(line);
                    if (batch.length() + mdRow.length() > maxBatchSize
                        && batch.length() > mdHeader.length()) {
                        batches.add(batch.toString());
                        batch.setLength(0);
                        batch.append(mdHeader);
                    }
                    batch.append(mdRow);
                }

                if (batch.length() > mdHeader.length()) {
                    batches.add(batch.toString());
                }
            } catch (final IOException e) {
                throw new UncheckedIOException("Failed to read CSV file: " + csvFile, e);
            }
            return batches;
        }, batches -> "buildBatchesFromCsv: file=" + csvFile.getFileName()
                + " maxRowsPerBatch=" + config.getMaxRowsPerBatch()
                + " \u2192 " + batches.size() + " batch(es)");
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
     * Pure conversational mode — no table attachments. Sends recent chat history
     * and the user's question to the LLM in a multi-turn format.
     */
    private String processConversational(final AskStroomAiRequest request,
                                         final ChatModel chatModel,
                                         final int chatId) {
        // Load recent conversation history.
        final List<AiChatMessage> history = aiService.getMessages(chatId);
        final List<ChatMessage> messages = new ArrayList<>();

        LOGGER.debug(() -> "processConversational: chatId=" + chatId
                + " historyMessages=" + history.size());

        messages.add(new SystemMessage(NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAIConfig::getChatSystemPrompt,
                AskStroomAIConfig.DEFAULT_CHAT_SYSTEM_PROMPT)));

        // Add recent message history (skip THINKING, TABLE_DATA, ATTACHMENT types).
        final int maxHistory = NullSafe.getOrElse(
                defaultConfigProvider.get(),
                AskStroomAIConfig::getMaxConversationHistoryMessages,
                AskStroomAIConfig.DEFAULT_MAX_CONVERSATION_HISTORY_MESSAGES);
        final List<AiChatMessage> relevantHistory = history.stream()
                .filter(m -> m.getMessageType() == AiMessageType.USER_MESSAGE
                             || m.getMessageType() == AiMessageType.AI_RESPONSE
                             || m.getMessageType() == AiMessageType.ERROR)
                .toList();

        // Take last N messages (skip the current message which was just stored).
        final int startIdx = Math.max(0, relevantHistory.size() - maxHistory - 1);
        for (int i = startIdx; i < relevantHistory.size() - 1; i++) {
            final AiChatMessage msg = relevantHistory.get(i);
            if (msg.getMessageType() == AiMessageType.USER_MESSAGE) {
                messages.add(new UserMessage(msg.getMessage()));
            } else {
                messages.add(new AiMessage(msg.getMessage()));
            }
        }

        // Add the current user message.
        messages.add(new UserMessage(request.getMessage()));

        LOGGER.trace(() -> "processConversational: sending " + messages.size()
                + " messages to LLM for chatId=" + chatId);

        final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                () -> chatModel.chat(messages),
                r -> "processConversational chatId=" + chatId
                     + " responseLength=" + r.aiMessage().text().length());
        LOGGER.trace(() -> "processConversational response:\n" + response.aiMessage().text());
        return response.aiMessage().text();
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
                AskStroomAIConfig::getMaxConversationHistoryMessages,
                AskStroomAIConfig.DEFAULT_MAX_CONVERSATION_HISTORY_MESSAGES);
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


    /**
     * Converts a CSV header line into a markdown table header with separator row.
     */
    private String buildMarkdownHeader(final String csvHeaderLine) {
        final List<String> cols = parseCsvLine(csvHeaderLine);
        final StringBuilder sb = new StringBuilder();
        for (final String col : cols) {
            sb.append("| ").append(col.trim()).append(" ");
        }
        sb.append("|\n");
        for (int i = 0; i < cols.size(); i++) {
            sb.append("| --- ");
        }
        sb.append("|\n");
        return sb.toString();
    }

    /**
     * Converts a CSV data line into a markdown table row.
     */
    private String csvLineToMarkdownRow(final String csvLine) {
        final List<String> cells = parseCsvLine(csvLine);
        final StringBuilder sb = new StringBuilder();
        for (final String cell : cells) {
            sb.append("| ").append(cell.trim()).append(" ");
        }
        sb.append("|\n");
        return sb.toString();
    }

    /**
     * Parses a single CSV line (RFC 4180) where fields are double-quoted and embedded
     * quotes are escaped as "". Handles commas and newlines within quoted fields.
     */
    static List<String> parseCsvLine(final String line) {
        final List<String> fields = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return fields;
        }

        final StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // Check for escaped quote (double-quote).
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++; // Skip the second quote.
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
        }
        fields.add(field.toString());
        return fields;
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
     * Escapes a field value for safe inclusion in a CSV file.
     * Wraps in double quotes if the value contains commas, quotes, or newlines.
     */
    private String escapeCsvField(final String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

    public ResultPage<AiChat> listChats(final FindNamedEntityCriteria criteria) {
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
        attachmentFileStore.deleteAttachmentFiles(attachmentIds); // cleanup CSV files
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
        globalConfigProvider.get().setInt(currentConfig,
                AskStroomAIConfig.PROP_NAME_MAX_CONVERSATION_HISTORY_MESSAGES,
                config.getMaxConversationHistoryMessages());
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
                TableAnalysisConfig.PROP_NAME_SUMMARY_MERGE_PROMPT,
                config.getSummaryMergePrompt());
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
                    // Stream the CSV from the attachment file store as a Markdown table.
                    writeAttachmentAsMarkdownTable(writer, msg.getAttachmentId(), msg.getMessage());
                } else {
                    final String body = msg.getMessage() != null ? msg.getMessage() : "";
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
     * Reads the CSV attachment file for the given attachment ID and writes it as a
     * Markdown table directly to the supplied writer. If the file is not found or
     * cannot be read, a fallback description line is written instead.
     */
    private void writeAttachmentAsMarkdownTable(final BufferedWriter writer,
                                                final int attachmentId,
                                                final String description) throws IOException {
        final Path csvFile = attachmentFileStore.getAttachmentFile(attachmentId);
        if (!Files.exists(csvFile)) {
            LOGGER.debug(() -> "writeAttachmentAsMarkdownTable: CSV not found for attachmentId="
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
        try (final BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                writer.write("_Empty data set_\n");
                return;
            }
            // Write Markdown table header + separator row.
            writer.write(buildMarkdownHeader(headerLine));
            // Stream data rows without buffering the whole file into memory.
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(csvLineToMarkdownRow(line));
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
