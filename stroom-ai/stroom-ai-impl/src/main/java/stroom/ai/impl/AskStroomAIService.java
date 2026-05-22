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
import stroom.ai.shared.TableSummaryConfig;
import stroom.config.global.api.GlobalConfig;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class AskStroomAIService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAIService.class);

    private static final Pattern MD_TABLE_ESCAPE = Pattern.compile("[$&`*_~#+-.!|()\\[\\]{}<>]");

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
    private final DashboardService dashboardService;
    private final QueryService queryService;
    private final OpenAIModelStore openAIModelStore;
    private final ResourceStore resourceStore;
    private final ExecutorProvider executorProvider;
    private final Provider<AskStroomAIConfig> defaultConfigProvider;
    private final Provider<GlobalConfig> globalConfigProvider;
    private final Provider<TableSummaryConfig> tableSummaryConfigProvider;
    private final ConcurrentHashMap<Integer, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    private final TaskContextFactory taskContextFactory;

    @Inject
    public AskStroomAIService(final AiService aiService,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore,
                              final ResourceStore resourceStore,
                              final ExecutorProvider executorProvider,
                              final Provider<AskStroomAIConfig> defaultConfigProvider,
                              final Provider<GlobalConfig> globalConfigProvider,
                              final Provider<TableSummaryConfig> tableSummaryConfigProvider,
                              final TaskContextFactory taskContextFactory) {
        this.aiService = aiService;
        this.dashboardService = dashboardService;
        this.queryService = queryService;
        this.openAIModelStore = openAIModelStore;
        this.resourceStore = resourceStore;
        this.executorProvider = executorProvider;
        this.defaultConfigProvider = defaultConfigProvider;
        this.globalConfigProvider = globalConfigProvider;
        this.tableSummaryConfigProvider = tableSummaryConfigProvider;
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

        // Stage 1: If context is present, create an attachment (and start async download if needed).
        if (request.getContext() != null && chatId != null) {
            try {
                createAttachment(chatId, request.getContext(), request.getConfig());
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                aiService.storeMessage(chatId, AiMessageType.ERROR, e.getMessage());
            }
        }

        // Stage 2: If a user message is present, process the question.
        if (request.getMessage() != null && chatId != null) {
            aiService.storeMessage(chatId, AiMessageType.USER_MESSAGE, request.getMessage());

            String responseText;
            try {
                responseText = processQuestion(request);

                if (responseText != null) {
                    aiService.storeMessage(chatId, AiMessageType.AI_RESPONSE, responseText);
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

    // ---- Attachment lifecycle ----

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

        final String contextJson = JsonUtil.writeValueAsString(context);
        final AiChatAttachment attachment = aiService.createAttachment(chatId, attachmentType, contextJson);

        // Store an ATTACHMENT message in the chat history linking to this attachment.
        aiService.storeMessage(chatId, AiMessageType.ATTACHMENT, attachment.getId(),
                attachmentType.name() + " table data");

        if (context instanceof final GeneralTableContext generalTableContext) {
            // Synchronous — data is already in memory, just convert to markdown.
            processGeneralAttachment(attachment.getId(), generalTableContext);
        } else {
            // Async download for Dashboard/Query contexts.
            submitAsyncDownload(attachment.getId(), chatId, context, config);
        }
    }

    /**
     * Converts GeneralTableContext (in-memory table data) directly to markdown and marks the
     * attachment as READY. No async download needed.
     */
    private void processGeneralAttachment(final int attachmentId,
                                          final GeneralTableContext generalTableContext) {
        try {
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.DOWNLOADING,
                    null, null, null, null);

            final String header = writeHeader(generalTableContext.getColumns());
            final StringBuilder markdown = new StringBuilder(header);
            int rowCount = 0;
            for (final List<String> rowValues : generalTableContext.getRows()) {
                markdown.append(writeRow(rowValues));
                rowCount++;
            }

            final String description = "Table data (" + rowCount + " rows, "
                                       + generalTableContext.getColumns().size() + " cols)";
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.READY,
                    markdown.toString(), rowCount, description, null);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.ERROR,
                    null, null, null, e.getMessage());
        }
    }

    /**
     * Submits an async task to download table data from a Dashboard or Query search,
     * convert it to markdown, and store it against the attachment record.
     */
    private void submitAsyncDownload(final int attachmentId,
                                     final int chatId,
                                     final AskStroomAiContext context,
                                     final AskStroomAIConfig config) {
        final Runnable runnable = taskContextFactory.context(
                "Download search results for AI analysis",
                taskContext -> {
                    try {
                        aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.DOWNLOADING,
                                null, null, null, null);

                        final Supplier<ResourceGeneration> downloadSupplier = buildDownloadSupplier(context);
                        final ResourceGeneration resourceGeneration = downloadSupplier.get();
                        final ResourceKey resourceKey = resourceGeneration.getResourceKey();

                        try {
                            final TableSummaryConfig tableSummaryConfig = getTableSummaryConfig(config);
                            final Path tempFile = resourceStore.getTempFile(resourceKey);
                            final int maximumRowCount = tableSummaryConfig.getMaximumTableInputRows();

                            final List<String> allLines;
                            try (final BufferedReader reader = Files.newBufferedReader(tempFile)) {
                                allLines = reader.lines()
                                        .limit(maximumRowCount + 1L)
                                        .toList();
                            }

                            if (allLines.isEmpty()) {
                                aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.READY,
                                        "", 0, "Empty table", null);
                                return;
                            }

                            // Convert CSV to markdown.
                            final String csvHeader = allLines.getFirst();
                            final String mdHeader = buildMarkdownHeader(csvHeader);
                            final int dataLineCount = allLines.size() - 1;

                            final StringBuilder markdown = new StringBuilder(mdHeader);
                            for (int i = 1; i < allLines.size(); i++) {
                                markdown.append(csvLineToMarkdownRow(allLines.get(i)));
                            }

                            final int colCount = parseCsvLine(csvHeader).size();
                            final String description = "Table data (" + dataLineCount + " rows, " + colCount + " cols)";

                            aiService.updateAttachmentStatus(attachmentId, AiAttachmentStatus.READY,
                                    markdown.toString(), dataLineCount, description, null);
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
                                null, null, null, e.getMessage());
                    }
                });
        executorProvider.get().execute(runnable);
    }

    /**
     * Builds the download supplier for Dashboard or Query contexts.
     */
    private Supplier<ResourceGeneration> buildDownloadSupplier(final AskStroomAiContext context) {
        if (context instanceof final DashboardTableContext ctx) {
            return () -> {
                final DashboardSearchRequest searchRequest = ctx.getSearchRequest();
                final String componentId = searchRequest.getComponentResultRequests().stream()
                        .filter(crr -> crr instanceof TableResultRequest)
                        .map(ComponentResultRequest::getComponentId)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No table component found"));
                final DownloadSearchResultsRequest downloadRequest = new DownloadSearchResultsRequest(
                        searchRequest, componentId, DownloadSearchResultFileType.CSV,
                        false, false, 100);
                return dashboardService.downloadSearchResults(downloadRequest);
            };
        } else if (context instanceof final QueryTableContext ctx) {
            return () -> {
                final QuerySearchRequest searchRequest = ctx.getSearchRequest();
                final DownloadQueryResultsRequest downloadRequest = new DownloadQueryResultsRequest(
                        searchRequest, DownloadSearchResultFileType.CSV, false, 100);
                return queryService.downloadSearchResults(downloadRequest);
            };
        }
        throw new IllegalStateException("Cannot build download supplier for: " + context.getClass().getName());
    }

    // ---- Question processing ----

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

        final AskStroomAIConfig config = request.getConfig();
        final ChatModel chatModel = getChatModel(config);

        // Create a single THINKING message that will be updated in place.
        final AiChatMessage thinkingMsg = aiService.storeMessage(
                chatId, AiMessageType.THINKING, "Thinking...");
        final int thinkingMessageId = thinkingMsg.getId();

        try {
            // Wait for any DOWNLOADING attachments to become READY.
            waitForAttachments(chatId, thinkingMessageId);

            // Check for READY attachments.
            final List<AiChatAttachment> attachments = aiService.getAttachmentsByChatId(chatId);
            final List<AiChatAttachment> readyAttachments = attachments.stream()
                    .filter(a -> a.getStatus() == AiAttachmentStatus.READY)
                    .toList();

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
     * Performs full batch analysis over all READY attachments, with conversation
     * history included as context in the prompt. Each question triggers a fresh analysis
     * so different aspects of the data can be explored.
     */
    private String analyseWithAttachments(final AskStroomAiRequest request,
                                          final ChatModel chatModel,
                                          final List<AiChatAttachment> attachments,
                                          final int chatId,
                                          final int thinkingMessageId) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        registerCancellation(chatId, cancelled);

        try {
            final TableSummaryConfig tableSummaryConfig = getTableSummaryConfig(request.getConfig());
            final String conversationContext = buildConversationSummary(chatId);

            // Combine all attachment data into a single markdown table for analysis.
            final StringBuilder allData = new StringBuilder();
            int totalRows = 0;
            for (final AiChatAttachment attachment : attachments) {
                final String data = aiService.getAttachmentData(attachment.getId());
                if (NullSafe.isNonBlankString(data)) {
                    allData.append(data);
                    if (!data.endsWith("\n")) {
                        allData.append('\n');
                    }
                    totalRows += NullSafe.getOrElse(attachment, AiChatAttachment::getRowCount, 0);
                }
            }

            if (allData.isEmpty()) {
                return "No data available for analysis.";
            }

            final String tableData = allData.toString();
            final int maxBatchSize = tableSummaryConfig.getMaximumBatchSize();

            // Estimate batches based on combined data size.
            final int estimatedBatches = Math.max(1, (tableData.length() + maxBatchSize - 1) / maxBatchSize);

            aiService.updateMessageText(thinkingMessageId,
                    "Analysing " + totalRows + " rows across "
                    + attachments.size() + " attachment(s)...");

            // Build an enhanced ResultBuilder that includes conversation context.
            final ResultBuilder resultBuilder = new ResultBuilder(
                    chatModel, request.getMessage(), tableSummaryConfig,
                    aiService, thinkingMessageId, estimatedBatches, cancelled, conversationContext);

            // Split table data into batches by character size and send to AI.
            // Find the header (first two lines: column names + separator).
            final String[] lines = tableData.split("\n", -1);
            String mdHeader = "";
            int dataStart = 0;
            if (lines.length >= 2 && lines[1].contains("---")) {
                mdHeader = lines[0] + "\n" + lines[1] + "\n";
                dataStart = 2;
            }

            final StringBuilder batch = new StringBuilder(mdHeader);
            for (int i = dataStart; i < lines.length; i++) {
                if (cancelled.get()) {
                    break;
                }
                final String line = lines[i];
                if (line.isEmpty()) {
                    continue;
                }
                final String row = line + "\n";
                if (batch.length() + row.length() > maxBatchSize && batch.length() > mdHeader.length()) {
                    resultBuilder.add(batch.toString());
                    batch.setLength(0);
                    batch.append(mdHeader);
                }
                batch.append(row);
            }
            if (batch.length() > mdHeader.length() && !cancelled.get()) {
                resultBuilder.add(batch.toString());
            }

            return resultBuilder.get();
        } finally {
            deregisterCancellation(chatId);
        }
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

        final ChatResponse response = chatModel.chat(messages);
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
                return;
            }

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

        return aiService.getChatModel(openAIModelDoc);
    }

    private TableSummaryConfig getTableSummaryConfig(final AskStroomAIConfig config) {
        return NullSafe.getOrElse(
                config,
                AskStroomAIConfig::getTableSummary,
                new TableSummaryConfig());
    }

    private String writeHeader(final List<String> columnList) {
        final StringBuilder sb = new StringBuilder();
        if (!columnList.isEmpty()) {
            columnList.forEach(cell -> {
                sb.append("| ");
                sb.append(escape(cell));
                sb.append(" ");
            });
            sb.append("|\n");
            columnList.forEach(cell -> {
                sb.append("| --- ");
            });
            sb.append("|\n");
        }
        return sb.toString();
    }

    private String writeRow(final List<String> rowValues) {
        final StringBuilder sb = new StringBuilder();
        if (!rowValues.isEmpty()) {
            rowValues.forEach(cell -> {
                sb.append("| ");
                sb.append(escape(cell));
                sb.append(" ");
            });
            sb.append("|\n");
        }
        return sb.toString();
    }

    private String escape(final String value) {
        if (value == null) {
            return "";
        }

        // Replace characters that can cause issues with Markdown tables
        return MD_TABLE_ESCAPE.matcher(value)
                .replaceAll("\\\\$0")
                .trim()
                .replace("\n", "<br>");
    }

    /**
     * Processes table data batches using direct ChatModel.chat() calls with configurable prompt templates.
     * For each batch, constructs a query prompt from the template, calls the LLM, and accumulates
     * partial summaries. If multiple batches are needed, merges partial summaries using the merge
     * prompt template.
     * <p>
     * Supports progressive status feedback (THINKING messages persisted to the chat) and
     * cancellation — if the cancelled flag is set, remaining batches are skipped and partial
     * results are returned.
     */
    static class ResultBuilder {

        private final ChatModel chatModel;
        private final String aiQuery;
        private final TableSummaryConfig config;
        private final AiService aiService;
        private final int thinkingMessageId;
        private final int totalBatches;
        private final AtomicBoolean cancelled;
        private final String conversationContext;
        private int batchCount;
        private String cumulativeSummary = "";

        public ResultBuilder(final ChatModel chatModel,
                             final String aiQuery,
                             final TableSummaryConfig config,
                             final AiService aiService,
                             final int thinkingMessageId,
                             final int totalBatches,
                             final AtomicBoolean cancelled,
                             final String conversationContext) {
            this.chatModel = chatModel;
            this.aiQuery = aiQuery;
            this.config = config;
            this.aiService = aiService;
            this.thinkingMessageId = thinkingMessageId;
            this.totalBatches = totalBatches;
            this.cancelled = cancelled;
            this.conversationContext = conversationContext != null
                    ? conversationContext
                    : "";
        }

        void add(final String data) {
            // Check cancellation before starting a new batch.
            if (cancelled.get()) {
                return;
            }

            batchCount++;

            // Update the single THINKING message with progress.
            final String suffix = batchCount > 1
                    ? " merging summaries"
                    : "";
            aiService.updateMessageText(thinkingMessageId,
                    "Analysing batch " + batchCount + "/" + totalBatches + "..." + suffix);

            // Build the query prompt from the configurable template
            final String systemPrompt = config.getTableQuerySystemPrompt() != null
                    ? config.getTableQuerySystemPrompt()
                    : TableSummaryConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
            final String userPromptTemplate = config.getTableQueryUserPrompt() != null
                    ? config.getTableQueryUserPrompt()
                    : TableSummaryConfig.DEFAULT_TABLE_QUERY_USER_PROMPT;

            final String userPrompt = userPromptTemplate
                    .replace("{{query}}", aiQuery)
                    .replace("{{table}}", data)
                    .replace("{{context}}", conversationContext);

            final List<ChatMessage> messages = new ArrayList<>(2);
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(userPrompt));

            final ChatResponse response = chatModel.chat(messages);
            if (cancelled.get()) {
                return;
            }
            final String batchAnswer = response.aiMessage().text();

            if (cumulativeSummary.isEmpty()) {
                cumulativeSummary = batchAnswer;
            } else {
                // Merge with the configurable merge prompt template
                final String mergePromptTemplate = config.getSummaryMergePrompt() != null
                        ? config.getSummaryMergePrompt()
                        : TableSummaryConfig.DEFAULT_SUMMARY_MERGE_PROMPT;

                final String mergePrompt = mergePromptTemplate
                        .replace("{{a}}", cumulativeSummary)
                        .replace("{{b}}", batchAnswer);

                final List<ChatMessage> mergeMessages = new ArrayList<>(2);
                mergeMessages.add(new SystemMessage(
                        "You merge partial answers into a unified, concise summary."));
                mergeMessages.add(new UserMessage(mergePrompt));

                final ChatResponse mergeResponse = chatModel.chat(mergeMessages);
                if (cancelled.get()) {
                    return;
                }
                cumulativeSummary = mergeResponse.aiMessage().text();
            }
        }

        String get() {
            if (cancelled.get()) {
                if (!cumulativeSummary.isEmpty()) {
                    return cumulativeSummary;
                }
                return "Analysis was cancelled before any results were produced.";
            }
            return cumulativeSummary;
        }
    }

    // ---- Cancellation methods ----

    void registerCancellation(final int chatId, final AtomicBoolean flag) {
        cancellationFlags.put(chatId, flag);
    }

    /**
     * Signals cancellation for an in-progress batch analysis.
     * The ResultBuilder checks this flag before each batch.
     */
    public boolean cancelProcessing(final int chatId) {
        aiService.verifyOwnership(chatId);
        final AtomicBoolean flag = cancellationFlags.get(chatId);
        if (flag != null) {
            flag.set(true);
            return true;
        }
        return false;
    }

    void deregisterCancellation(final int chatId) {
        cancellationFlags.remove(chatId);
    }

    // ---- Chat management methods ----

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
        aiService.deleteChat(chatId);
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
        // Conversation is complete if there are no THINKING messages among the new messages.
        final boolean complete = newMessages.stream()
                .noneMatch(msg -> msg.getMessageType() == AiMessageType.THINKING);
        return new AiChatPollResponse(newMessages, complete);
    }

    // ---- Config methods ----

    public AskStroomAIConfig getDefaultConfig() {
        return defaultConfigProvider.get();
    }

    public Boolean setDefaultAskStroomAIConfig(final AskStroomAIConfig config) {
        setDefaultModel(config.getModelRef());
        setDefaultTableSummaryConfig(config.getTableSummary());

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

    private Boolean setDefaultTableSummaryConfig(final TableSummaryConfig config) {
        final TableSummaryConfig defaultTableSummaryConfig = tableSummaryConfigProvider.get();
        globalConfigProvider.get().setInt(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_MAXIMUM_BATCH_SIZE,
                config.getMaximumBatchSize());
        globalConfigProvider.get().setInt(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS,
                config.getMaximumTableInputRows());
        globalConfigProvider.get().setString(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_TABLE_QUERY_SYSTEM_PROMPT,
                config.getTableQuerySystemPrompt());
        globalConfigProvider.get().setString(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_TABLE_QUERY_USER_PROMPT,
                config.getTableQueryUserPrompt());
        globalConfigProvider.get().setString(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_SUMMARY_MERGE_PROMPT,
                config.getSummaryMergePrompt());
        return true;
    }
}
