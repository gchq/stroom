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
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.Column;
import stroom.query.api.OffsetRange;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class AskStroomAIService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAIService.class);

    private static final Pattern MD_TABLE_ESCAPE = Pattern.compile("[$&`*_~#+-.!|()\\[\\]{}<>]");

    private final AiService aiService;
    private final DashboardService dashboardService;
    private final QueryService queryService;
    private final OpenAIModelStore openAIModelStore;
    private final Provider<AskStroomAIConfig> defaultConfigProvider;
    private final Provider<GlobalConfig> globalConfigProvider;
    private final Provider<TableSummaryConfig> tableSummaryConfigProvider;
    private final SecurityContext securityContext;

    @Inject
    public AskStroomAIService(final AiService aiService,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore,
                              final Provider<AskStroomAIConfig> defaultConfigProvider,
                              final Provider<GlobalConfig> globalConfigProvider,
                              final Provider<TableSummaryConfig> tableSummaryConfigProvider,
                              final SecurityContext securityContext) {
        this.aiService = aiService;
        this.dashboardService = dashboardService;
        this.queryService = queryService;
        this.openAIModelStore = openAIModelStore;
        this.defaultConfigProvider = defaultConfigProvider;
        this.globalConfigProvider = globalConfigProvider;
        this.tableSummaryConfigProvider = tableSummaryConfigProvider;
        this.securityContext = securityContext;
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
        if (request.getContext() instanceof final DashboardTableContext dashboardTableContext) {
            final Function<OffsetRange, DashboardSearchResponse> dataProvider = range -> {
                DashboardSearchRequest searchRequest = dashboardTableContext.getSearchRequest();
                final ComponentResultRequest componentResultRequest =
                        searchRequest.getComponentResultRequests().getFirst();
                if (componentResultRequest instanceof TableResultRequest tableResultRequest) {
                    tableResultRequest = tableResultRequest.copy().requestedRange(range).build();
                }
                throw new RuntimeException("No table component provided");
            };
            return createStroomAiTableSummary(
                    request,
                    dataProvider);

        } else if (request.getContext() instanceof final QueryTableContext queryTableContext) {
            final Function<OffsetRange, DashboardSearchResponse> dataProvider = range -> {
                final QuerySearchRequest searchRequest = queryTableContext
                        .getSearchRequest()
                        .copy()
                        .requestedRange(range)
                        .build();
                return queryService.search(searchRequest);
            };
            return createStroomAiTableSummary(
                    request,
                    dataProvider);
        } else if (request.getContext() instanceof final GeneralTableContext generalTableContext) {
            return createGeneralAiTableSummary(
                    request,
                    generalTableContext);
        }

        throw new IllegalStateException();
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createGeneralAiTableSummary(final AskStroomAiRequest request,
                                               final GeneralTableContext generalTableData) {
        try {
            final AskStroomAIConfig config = request.getConfig();
            final ChatModel chatModel = getChatModel(config);
            final TableSummaryConfig tableSummaryConfig = getTableSummaryConfig(config);
            final ResultBuilder resultBuilder = new ResultBuilder(
                    chatModel, request.getMessage(), tableSummaryConfig);

            // Create column header string.
            final String header = writeHeader(generalTableData.getColumns());

            // Batch and summarise user message responses into a combined summary
            final int maxBatchSize = tableSummaryConfig.getMaximumBatchSize();
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

        return aiService.getChatModel(openAIModelDoc);
    }

    private TableSummaryConfig getTableSummaryConfig(final AskStroomAIConfig config) {
        return NullSafe.getOrElse(
                config,
                AskStroomAIConfig::getTableSummary,
                new TableSummaryConfig());
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createStroomAiTableSummary(final AskStroomAiRequest request,
                                              final Function<OffsetRange, DashboardSearchResponse> dataProvider) {
        try {
            final AskStroomAIConfig config = request.getConfig();
            final ChatModel chatModel = getChatModel(config);
            final TableSummaryConfig tableSummaryConfig = getTableSummaryConfig(config);
            final ResultBuilder resultBuilder = new ResultBuilder(
                    chatModel, request.getMessage(), tableSummaryConfig);

            // Get the first result page from the data source.
            OffsetRange range = new OffsetRange(0, 100);
            DashboardSearchResponse response = dataProvider.apply(range);
            TableResult result = (TableResult) response.getResults().getFirst();

            // Create column header string from visible columns.
            final List<Column> columns = result.getColumns();
            final String header = writeHeader(columns
                    .stream()
                    .filter(Column::isVisible)
                    .map(Column::getName)
                    .toList());

            // Batch and summarise user message responses into a combined summary
            final int maxBatchSize = tableSummaryConfig.getMaximumBatchSize();
            final int maximumRowCount = tableSummaryConfig.getMaximumTableInputRows();
            final StringBuilder batch = new StringBuilder(header);
            int rowCount = 0;

            while (!NullSafe.isEmptyCollection(result.getRows())) {
                for (final Row row : result.getRows()) {
                    final List<String> rowValues = row.getValues();

                    // Write row
                    final StringBuilder rowBuilder = new StringBuilder();
                    for (int i = 0; i < columns.size(); i++) {
                        final Column column = columns.get(i);
                        // Only write visible cells.
                        if (column.isVisible()) {
                            final String cell = rowValues.get(i);
                            rowBuilder.append("| ");
                            rowBuilder.append(escape(cell));
                            rowBuilder.append(" ");
                        }
                    }
                    // End the row
                    if (!rowBuilder.isEmpty()) {
                        rowBuilder.append("|\n");
                    }

                    final String rowString = rowBuilder.toString();
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

                // Exit as soon as we have reached the maximum row count.
                if (rowCount >= maximumRowCount) {
                    break;
                }

                // Get the next result page from the data source.
                range = new OffsetRange(range.getOffset() + 100L, 100L);
                response = dataProvider.apply(range);
                result = (TableResult) response.getResults().getFirst();
            }

            if (!batch.isEmpty()) {
                // Process any remaining batch content
                resultBuilder.add(batch.toString());
            }

            return resultBuilder.get();

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return e.getMessage();
        }
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
     */
    static class ResultBuilder {

        private final ChatModel chatModel;
        private final String aiQuery;
        private final TableSummaryConfig config;
        private String cumulativeSummary = "";

        public ResultBuilder(final ChatModel chatModel,
                             final String aiQuery,
                             final TableSummaryConfig config) {
            this.chatModel = chatModel;
            this.aiQuery = aiQuery;
            this.config = config;
        }

        void add(final String data) {
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
            return cumulativeSummary;
        }
    }

    // ---- Chat management methods ----

    public AiChat createChat() {
        final UserRef userRef = securityContext.getUserRef();
        return aiService.createChat(userRef);
    }

    public List<AiChat> listChats() {
        final String userUuid = securityContext.getUserRef().getUuid();
        return aiService.listChats(userUuid);
    }

    public AiChat getChat(final int chatId) {
        final AiChat chat = aiService.getChat(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found: " + chatId));
        verifyOwnership(chat);
        return chat;
    }

    public void deleteChat(final int chatId) {
        verifyOwnership(chatId);
        aiService.deleteChat(chatId);
    }

    public void updateChatTitle(final int chatId, final String title) {
        verifyOwnership(chatId);
        aiService.updateChatTitle(chatId, title);
    }

    public List<AiChatMessage> getMessages(final int chatId) {
        verifyOwnership(chatId);
        return aiService.getMessages(chatId);
    }

    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final String message) {
        verifyOwnership(chatId);
        return aiService.storeMessage(chatId, messageType, message);
    }

    private void verifyOwnership(final int chatId) {
        final AiChat chat = aiService.getChat(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found: " + chatId));
        verifyOwnership(chat);
    }

    private void verifyOwnership(final AiChat chat) {
        final String currentUserUuid = securityContext.getUserRef().getUuid();
        if (!currentUserUuid.equals(chat.getUserUuid())) {
            throw new RuntimeException("Access denied: chat " + chat.getId()
                                       + " does not belong to the current user");
        }
    }

    public AiChatPollResponse pollMessages(final int chatId, final AiChatPollRequest request) {
        verifyOwnership(chatId);
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
