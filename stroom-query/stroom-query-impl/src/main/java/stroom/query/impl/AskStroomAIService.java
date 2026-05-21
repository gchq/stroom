package stroom.query.impl;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAIConfig;
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
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.svg.shared.SvgImage;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FindNamedEntityCriteria;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AskStroomAIService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAIService.class);

    private static final Pattern MD_TABLE_ESCAPE = Pattern.compile("[$&`*_~#+-.!|()\\[\\]{}<>]");

    /**
     * Sentinel model ID that activates the stub ChatModel for offline testing.
     * Create an OpenAIModel document with this as the modelId — no API key or base URL needed.
     */
    static final String STUB_MODEL_ID = "__stub__";

    private final AiService aiService;
    private final DashboardService dashboardService;
    private final QueryService queryService;
    private final OpenAIModelStore openAIModelStore;
    private final ResourceStore resourceStore;
    private final Provider<AskStroomAIConfig> defaultConfigProvider;
    private final Provider<GlobalConfig> globalConfigProvider;
    private final Provider<TableSummaryConfig> tableSummaryConfigProvider;
    private final ConcurrentHashMap<Integer, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    @Inject
    public AskStroomAIService(final AiService aiService,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore,
                              final ResourceStore resourceStore,
                              final Provider<AskStroomAIConfig> defaultConfigProvider,
                              final Provider<GlobalConfig> globalConfigProvider,
                              final Provider<TableSummaryConfig> tableSummaryConfigProvider) {
        this.aiService = aiService;
        this.dashboardService = dashboardService;
        this.queryService = queryService;
        this.openAIModelStore = openAIModelStore;
        this.resourceStore = resourceStore;
        this.defaultConfigProvider = defaultConfigProvider;
        this.globalConfigProvider = globalConfigProvider;
        this.tableSummaryConfigProvider = tableSummaryConfigProvider;
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    public AskStroomAiResponse askStroomAi(final AskStroomAiRequest request) {
        // Persist the user's message if a chat is associated.
        final AiChat aiChat = request.getAiChat();
        if (aiChat != null && request.getMessage() != null) {
            aiService.storeMessage(aiChat.getId(), AiMessageType.USER_MESSAGE, request.getMessage());
        }

        String responseText;
        try {
            responseText = processRequest(request);

            // Persist the AI response on success.
            if (aiChat != null && responseText != null) {
                aiService.storeMessage(aiChat.getId(), AiMessageType.AI_RESPONSE, responseText);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            responseText = e.getMessage();
            // Persist error only.
            if (aiChat != null) {
                aiService.storeMessage(aiChat.getId(), AiMessageType.ERROR, responseText);
            }
        }

        return new AskStroomAiResponse(responseText);
    }

    private String processRequest(final AskStroomAiRequest request) {
        if (request.getContext() instanceof final DashboardTableContext ctx) {
            return downloadAndAnalyse(request, () -> {
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
            });

        } else if (request.getContext() instanceof final QueryTableContext ctx) {
            return downloadAndAnalyse(request, () -> {
                final QuerySearchRequest searchRequest = ctx.getSearchRequest();
                final DownloadQueryResultsRequest downloadRequest = new DownloadQueryResultsRequest(
                        searchRequest, DownloadSearchResultFileType.CSV, false, 100);
                return queryService.downloadSearchResults(downloadRequest);
            });

        } else if (request.getContext() instanceof final GeneralTableContext generalTableContext) {
            return createGeneralAiTableSummary(request, generalTableContext);
        }

        throw new IllegalStateException("Unsupported context type");
    }

    /**
     * Downloads table data to a CSV temp file via the existing download infrastructure,
     * then reads the file line-by-line, batches the content, and sends each batch to
     * the LLM for analysis. Progress messages are persisted to the chat as THINKING messages.
     */
    private String downloadAndAnalyse(final AskStroomAiRequest request,
                                      final Supplier<ResourceGeneration> downloadSupplier) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        if (chatId != null) {
            registerCancellation(chatId, cancelled);
        }
        ResourceKey resourceKey = null;
        try {
            final AskStroomAIConfig config = request.getConfig();
            final ChatModel chatModel = getChatModel(config);
            final TableSummaryConfig tableSummaryConfig = getTableSummaryConfig(config);

            // Phase B1: Download all data to a CSV temp file.
            if (chatId != null) {
                aiService.storeMessage(chatId, AiMessageType.THINKING,
                        "--- Downloading table data...");
            }

            final ResourceGeneration resourceGeneration = downloadSupplier.get();
            resourceKey = resourceGeneration.getResourceKey();
            final Path tempFile = resourceStore.getTempFile(resourceKey);

            // Phase B2: Read CSV file and batch for AI.
            final int maxBatchSize = tableSummaryConfig.getMaximumBatchSize();
            final int maximumRowCount = tableSummaryConfig.getMaximumTableInputRows();

            // Read lines (limited to max row count + header).
            final List<String> allLines;
            try (final BufferedReader reader = Files.newBufferedReader(tempFile)) {
                allLines = reader.lines()
                        .limit(maximumRowCount + 1L)
                        .collect(Collectors.toList());
            }

            if (allLines.isEmpty()) {
                return "No data available for analysis.";
            }

            // Build markdown header from the CSV header line.
            final String csvHeader = allLines.getFirst();
            final String mdHeader = buildMarkdownHeader(csvHeader);

            final int dataLineCount = allLines.size() - 1;

            if (chatId != null) {
                aiService.storeMessage(chatId, AiMessageType.THINKING,
                        "--- Downloaded " + dataLineCount + " rows");
            }

            // Store a preview of the first few rows as TABLE_DATA.
            if (chatId != null) {
                final int previewRows = Math.min(5, dataLineCount);
                final StringBuilder preview = new StringBuilder(mdHeader);
                for (int i = 1; i <= previewRows; i++) {
                    preview.append(csvLineToMarkdownRow(allLines.get(i)));
                }
                aiService.storeMessage(chatId, AiMessageType.TABLE_DATA, preview.toString());
            }

            // Estimate total batches.
            final int totalChars = allLines.stream().skip(1).mapToInt(String::length).sum();
            final int estimatedBatches = Math.max(1, (totalChars + maxBatchSize - 1) / maxBatchSize);

            final ResultBuilder resultBuilder = new ResultBuilder(
                    chatModel, request.getMessage(), tableSummaryConfig,
                    aiService, chatId, estimatedBatches, cancelled);

            // Batch data lines and send to AI.
            final StringBuilder batch = new StringBuilder(mdHeader);
            for (int i = 1; i < allLines.size(); i++) {
                if (cancelled.get()) {
                    break;
                }
                final String mdRow = csvLineToMarkdownRow(allLines.get(i));
                if (batch.length() + mdRow.length() > maxBatchSize && batch.length() > mdHeader.length()) {
                    resultBuilder.add(batch.toString());
                    batch.setLength(0);
                    batch.append(mdHeader);
                }
                batch.append(mdRow);
            }
            if (batch.length() > mdHeader.length() && !cancelled.get()) {
                resultBuilder.add(batch.toString());
            }

            return resultBuilder.get();

        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
            return "Error reading downloaded data: " + e.getMessage();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return e.getMessage();
        } finally {
            // Clean up temp file and cancellation registration.
            if (resourceKey != null) {
                try {
                    resourceStore.deleteTempFile(resourceKey);
                } catch (final Exception e) {
                    LOGGER.debug(() -> "Failed to delete temp file", e);
                }
            }
            if (chatId != null) {
                deregisterCancellation(chatId);
            }
        }
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

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createGeneralAiTableSummary(final AskStroomAiRequest request,
                                               final GeneralTableContext generalTableData) {
        final Integer chatId = NullSafe.get(request.getAiChat(), AiChat::getId);
        try {
            final AskStroomAIConfig config = request.getConfig();
            final ChatModel chatModel = getChatModel(config);
            final TableSummaryConfig tableSummaryConfig = getTableSummaryConfig(config);

            // Estimate total batches for progress display.
            final int maxBatchSize = tableSummaryConfig.getMaximumBatchSize();
            final int estimatedDataSize = generalTableData.getRows().stream()
                    .mapToInt(row -> row.stream().mapToInt(s -> s == null
                            ? 0
                            : s.length()).sum())
                    .sum();
            final int estimatedBatches = Math.max(1, (estimatedDataSize + maxBatchSize - 1) / maxBatchSize);

            final AtomicBoolean cancelled = new AtomicBoolean(false);
            if (chatId != null) {
                registerCancellation(chatId, cancelled);
            }

            final ResultBuilder resultBuilder = new ResultBuilder(
                    chatModel, request.getMessage(), tableSummaryConfig,
                    aiService, chatId, estimatedBatches, cancelled);

            // Create column header string.
            final String header = writeHeader(generalTableData.getColumns());

            // Batch and summarise user message responses into a combined summary
            final int maximumRowCount = tableSummaryConfig.getMaximumTableInputRows();
            final StringBuilder batch = new StringBuilder(header);
            int rowCount = 0;

            for (final List<String> rowValues : generalTableData.getRows()) {
                final String rowString = writeRow(rowValues);
                final int newBatchSize = batch.length() + rowString.length();
                if (rowCount > 0 && newBatchSize > maxBatchSize) {
                    // Batch message plus the new row would exceed the maximum batch size, so send the batch to the
                    // model as-is
                    resultBuilder.add(batch.toString());
                    batch.setLength(0);
                    batch.append(header);
                }

                batch.append(rowString);
                rowCount++;

                // Exit as soon as we have reached the maximum row count.
                if (rowCount >= maximumRowCount) {
                    break;
                }
            }

            if (!batch.isEmpty()) {
                // Process any remaining batch content
                resultBuilder.add(batch.toString());
            }

            return resultBuilder.get();

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return e.getMessage();
        } finally {
            if (chatId != null) {
                deregisterCancellation(chatId);
            }
        }
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
        private final Integer chatId;
        private final int totalBatches;
        private final AtomicBoolean cancelled;
        private int batchCount;
        private String cumulativeSummary = "";

        public ResultBuilder(final ChatModel chatModel,
                             final String aiQuery,
                             final TableSummaryConfig config,
                             final AiService aiService,
                             final Integer chatId,
                             final int totalBatches,
                             final AtomicBoolean cancelled) {
            this.chatModel = chatModel;
            this.aiQuery = aiQuery;
            this.config = config;
            this.aiService = aiService;
            this.chatId = chatId;
            this.totalBatches = totalBatches;
            this.cancelled = cancelled;
        }

        void add(final String data) {
            // Check cancellation before starting a new batch.
            if (cancelled.get()) {
                return;
            }

            batchCount++;

            // Persist progress message.
            if (chatId != null && aiService != null) {
                final String suffix = batchCount > 1
                        ? " merging summaries"
                        : "";
                aiService.storeMessage(chatId, AiMessageType.THINKING,
                        "--- Analysing batch " + batchCount + "/" + totalBatches + "..." + suffix);
            }

            // Build the query prompt from the configurable template
            final String systemPrompt = config.getTableQuerySystemPrompt() != null
                    ? config.getTableQuerySystemPrompt()
                    : TableSummaryConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
            final String userPromptTemplate = config.getTableQueryUserPrompt() != null
                    ? config.getTableQueryUserPrompt()
                    : TableSummaryConfig.DEFAULT_TABLE_QUERY_USER_PROMPT;

            final String userPrompt = userPromptTemplate
                    .replace("{{query}}", aiQuery)
                    .replace("{{table}}", data);

            final List<ChatMessage> messages = new ArrayList<>(2);
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(userPrompt));

            final ChatResponse response = chatModel.chat(messages);
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
                cumulativeSummary = mergeResponse.aiMessage().text();
            }
        }

        String get() {
            if (cancelled.get() && !cumulativeSummary.isEmpty()) {
                return SvgImage.ALERT.getSvg() + " *Analysis cancelled after " + batchCount + "/" + totalBatches
                       + " batches.*\n\n" + cumulativeSummary;
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
