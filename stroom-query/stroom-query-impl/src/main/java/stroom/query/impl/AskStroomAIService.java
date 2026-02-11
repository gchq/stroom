package stroom.query.impl;

import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.ChatMemoryConfig;
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
import stroom.langchain.api.ChatMemoryService;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.langchain.api.SimpleTokenCountEstimator;
import stroom.langchain.api.SummaryReducer;
import stroom.langchain.api.TableQuery;
import stroom.langchain.api.TableQueryMessages;
import stroom.langchain.api.TableSummaryMessages;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.Column;
import stroom.query.api.OffsetRange;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class AskStroomAIService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAIService.class);

    private static final String TABLE_CHAT_MEMORY_KEY = "table";
    private static final String SUMMARY_CHAT_MEMORY_KEY = "summary";
    private static final Pattern MD_TABLE_ESCAPE = Pattern.compile("[$&`*_~#+-.!|()\\[\\]{}<>]");

    private final OpenAIService openAIService;
    private final ChatMemoryService chatMemoryService;
    private final DashboardService dashboardService;
    private final QueryService queryService;
    private final OpenAIModelStore openAIModelStore;
    private final Provider<AskStroomAIConfig> defaultConfigProvider;
    private final Provider<GlobalConfig> globalConfigProvider;
    private final Provider<TableSummaryConfig> tableSummaryConfigProvider;
    private final Provider<ChatMemoryConfig> chatMemoryConfigProvider;

    @Inject
    public AskStroomAIService(final OpenAIService openAIService,
                              final ChatMemoryService chatMemoryService,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore,
                              final Provider<AskStroomAIConfig> defaultConfigProvider,
                              final Provider<GlobalConfig> globalConfigProvider,
                              final Provider<TableSummaryConfig> tableSummaryConfigProvider,
                              final Provider<ChatMemoryConfig> chatMemoryConfigProvider) {
        this.openAIService = openAIService;
        this.chatMemoryService = chatMemoryService;
        this.dashboardService = dashboardService;
        this.queryService = queryService;
        this.openAIModelStore = openAIModelStore;
        this.defaultConfigProvider = defaultConfigProvider;
        this.globalConfigProvider = globalConfigProvider;
        this.tableSummaryConfigProvider = tableSummaryConfigProvider;
        this.chatMemoryConfigProvider = chatMemoryConfigProvider;
    }

    /**
     * Dashboard search queries and StroomQL queries will run on specific nodes so find out which node that is.
     *
     * @param nodeName The provided search node.
     * @param request  The ask stroom AI request.
     * @return The node to use or null if the current node is ok.
     */
    public String getBestNode(final String nodeName, final AskStroomAiRequest request) {
        if (request.getContext() instanceof final DashboardTableContext dashboardTableContext) {
            return dashboardService.getBestNode(nodeName, dashboardTableContext.getSearchRequest());
        } else if (request.getContext() instanceof final QueryTableContext queryTableContext) {
            return queryService.getBestNode(nodeName, queryTableContext.getSearchRequest());
        }
        return null;
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    public AskStroomAiResponse askStroomAi(final AskStroomAiRequest request) {
        if (request.getContext() instanceof final DashboardTableContext dashboardTableContext) {
            final Function<OffsetRange, DashboardSearchResponse> dataProvider = range -> {
                DashboardSearchRequest searchRequest = dashboardTableContext.getSearchRequest();
                final ComponentResultRequest componentResultRequest =
                        searchRequest.getComponentResultRequests().getFirst();
                if (componentResultRequest instanceof TableResultRequest tableResultRequest) {
                    tableResultRequest = tableResultRequest.copy().requestedRange(range).build();
                    searchRequest = dashboardTableContext.getSearchRequest()
                            .copy()
                            .componentResultRequests(Collections.singletonList(tableResultRequest))
                            .build();
                    return dashboardService.search(searchRequest);
                }
                throw new RuntimeException("No table component provided");
            };
            return new AskStroomAiResponse(createStroomAiTableSummary(
                    request,
                    dataProvider));

        } else if (request.getContext() instanceof final QueryTableContext queryTableContext) {
            final Function<OffsetRange, DashboardSearchResponse> dataProvider = range -> {
                final QuerySearchRequest searchRequest = queryTableContext
                        .getSearchRequest()
                        .copy()
                        .requestedRange(range)
                        .build();
                return queryService.search(searchRequest);
            };
            return new AskStroomAiResponse(createStroomAiTableSummary(
                    request,
                    dataProvider));
        } else if (request.getContext() instanceof final GeneralTableContext generalTableContext) {
            return new AskStroomAiResponse(createGeneralAiTableSummary(
                    request,
                    generalTableContext));
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
            final ResultBuilder resultBuilder = createResultBuilder(
                    request, request.getMessage());

            // Create column header string.
            final String header = writeHeader(generalTableData.getColumns());

            // Batch and summarise user message responses into a combined summary
            final TableSummaryConfig tableSummaryConfig = NullSafe.getOrElse(
                    config,
                    AskStroomAIConfig::getTableSummary,
                    new TableSummaryConfig());
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

    private ChatModel getChatModel(final DocRef docRef) {
        if (!OpenAIModelDoc.TYPE.equals(docRef.getType())) {
            throw new RuntimeException("Default OpenAI API doc ref is incorrect");
        }

        final OpenAIModelDoc openAIModelDoc = openAIModelStore.readDocument(docRef);
        if (openAIModelDoc == null) {
            throw new RuntimeException("Default OpenAI API doc cannot be found");
        }

        return openAIService.getChatModel(openAIModelDoc);
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createStroomAiTableSummary(final AskStroomAiRequest request,
                                              final Function<OffsetRange, DashboardSearchResponse> dataProvider) {
        try {
            final AskStroomAIConfig config = request.getConfig();
            final ResultBuilder resultBuilder = createResultBuilder(
                    request,
                    request.getMessage());

//            final ResultBuilder2 resultBuilder = new ResultBuilder2(chatModel, aiQuery);

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
            final TableSummaryConfig tableSummaryConfig = NullSafe.getOrElse(
                    config,
                    AskStroomAIConfig::getTableSummary,
                    new TableSummaryConfig());
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

    private ResultBuilder createResultBuilder(final AskStroomAiRequest request,
                                              final String aiQuery) {
        final AskStroomAIConfig modelConfig = request.getConfig();
        if (modelConfig == null || modelConfig.getModelRef() == null) {
            throw new RuntimeException("No model specified");
        }
        final DocRef docRef = modelConfig.getModelRef();

        final ChatModel chatModel = getChatModel(docRef);
        final String chatMemoryId = request.getContext().getChatMemoryId();
        final String tableChatMemoryId = TABLE_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        final String summaryChatMemoryId = SUMMARY_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        final int maxTokens = request.getConfig().getChatMemory().getTokenLimit();

        final TableQuery tableQueryService = AiServices.builder(TableQuery.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> TokenWindowChatMemory.builder()
                        .chatMemoryStore(chatMemoryService.getChatMemoryStore())
                        .id(tableChatMemoryId)
                        .maxTokens(maxTokens, new SimpleTokenCountEstimator())
                        .build())
                .build();
        final SummaryReducer summaryReducerService = AiServices.builder(SummaryReducer.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> TokenWindowChatMemory.builder()
                        .chatMemoryStore(chatMemoryService.getChatMemoryStore())
                        .id(summaryChatMemoryId)
                        .maxTokens(maxTokens, new SimpleTokenCountEstimator())
                        .build())
                .build();
        return new ResultBuilder(chatMemoryId, aiQuery, tableQueryService, summaryReducerService);
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

    static class ResultBuilder {

        private final String aiQuery;
        private final TableQuery tableQueryService;
        private final SummaryReducer summaryReducerService;
        private final String tableChatMemoryId;
        private final String summaryChatMemoryId;
        private String cumulativeSummary = "";

        public ResultBuilder(final String chatMemoryId,
                             final String aiQuery,
                             final TableQuery tableQueryService,
                             final SummaryReducer summaryReducerService) {
            this.aiQuery = aiQuery;
            this.tableQueryService = tableQueryService;
            this.summaryReducerService = summaryReducerService;
            tableChatMemoryId = TABLE_CHAT_MEMORY_KEY + "/" + chatMemoryId;
            summaryChatMemoryId = SUMMARY_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        }

        void add(final String data) {
            // Process any remaining batch content
            final String batchAnswer = tableQueryService.answerChunk(
                    tableChatMemoryId, aiQuery, data);
            if (cumulativeSummary.isEmpty()) {
                cumulativeSummary = batchAnswer;
            } else {
                cumulativeSummary = summaryReducerService.merge(
                        summaryChatMemoryId, cumulativeSummary, batchAnswer);
            }
        }

        String get() {
            return cumulativeSummary;
        }
    }

    static class ResultBuilder2 {

        private final ChatModel chatModel;
        private final String aiQuery;
        private String cumulativeSummary = "";

        public ResultBuilder2(final ChatModel chatModel,
                              final String aiQuery) {
            this.chatModel = chatModel;
            this.aiQuery = aiQuery;
        }

        void add(final String data) {
            // Process any remaining batch content
            final String batchAnswer = chatModel.chat(TableQueryMessages.createMessages(aiQuery, data))
                    .aiMessage()
                    .text();
            if (cumulativeSummary.isEmpty()) {
                cumulativeSummary = batchAnswer;
            } else {
                cumulativeSummary = chatModel.chat(TableSummaryMessages.createMessages(cumulativeSummary, batchAnswer))
                        .aiMessage()
                        .text();
            }
        }

        String get() {
            return cumulativeSummary;
        }
    }

    public AskStroomAIConfig getDefaultConfig() {
        return defaultConfigProvider.get();
    }

    public Boolean setDefaultModel(final DocRef modelRef) {
        globalConfigProvider.get().setDocRef(getDefaultConfig(), AskStroomAIConfig.PROP_NAME_MODEL_REF, modelRef);
        return true;
    }

    public Boolean setDefaultTableSummaryConfig(final TableSummaryConfig config) {
        final TableSummaryConfig defaultTableSummaryConfig = tableSummaryConfigProvider.get();
        globalConfigProvider.get().setInt(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_MAXIMUM_BATCH_SIZE,
                config.getMaximumBatchSize());
        globalConfigProvider.get().setInt(defaultTableSummaryConfig,
                TableSummaryConfig.PROP_NAME_MAXIMUM_TABLE_INPUT_ROWS,
                config.getMaximumTableInputRows());
        return true;
    }

    public Boolean setDefaultChatMemoryConfigConfig(final ChatMemoryConfig config) {
        final ChatMemoryConfig defaultChatMemoryConfig = chatMemoryConfigProvider.get();
        globalConfigProvider.get().setInt(defaultChatMemoryConfig,
                ChatMemoryConfig.PROP_NAME_TOKEN_LIMIT,
                config.getTokenLimit());
        globalConfigProvider.get().setString(defaultChatMemoryConfig,
                ChatMemoryConfig.PROP_NAME_TIME_TO_LIVE,
                config.getTimeToLive().toString());
        return true;
    }
}
