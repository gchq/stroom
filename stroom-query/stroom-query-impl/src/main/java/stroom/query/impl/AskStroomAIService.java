package stroom.query.impl;

import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.DashboardTableData;
import stroom.ai.shared.GeneralTableData;
import stroom.ai.shared.QueryTableData;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.langchain.api.ChatMemoryConfig;
import stroom.langchain.api.ChatMemoryService;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.langchain.api.SimpleTokenCountEstimator;
import stroom.langchain.api.SummaryReducer;
import stroom.langchain.api.TableQuery;
import stroom.langchain.api.TableQueryMessages;
import stroom.langchain.api.TableSummaryMessages;
import stroom.openai.shared.OpenAIModelConfig;
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

    private final Provider<OpenAIModelConfig> openAiModelConfigProvider;
    private final Provider<ChatMemoryConfig> chatMemoryConfigProvider;
    private final OpenAIService openAIService;
    private final ChatMemoryService chatMemoryService;
    private final DashboardService dashboardService;
    private final QueryService queryService;
    private final OpenAIModelStore openAIModelStore;

    @Inject
    public AskStroomAIService(final Provider<OpenAIModelConfig> openAiModelConfigProvider,
                              final Provider<ChatMemoryConfig> chatMemoryConfigProvider,
                              final OpenAIService openAIService,
                              final ChatMemoryService chatMemoryService,
                              final DashboardService dashboardService,
                              final QueryService queryService,
                              final OpenAIModelStore openAIModelStore) {
        this.openAiModelConfigProvider = openAiModelConfigProvider;
        this.chatMemoryConfigProvider = chatMemoryConfigProvider;
        this.openAIService = openAIService;
        this.chatMemoryService = chatMemoryService;
        this.dashboardService = dashboardService;
        this.queryService = queryService;
        this.openAIModelStore = openAIModelStore;
    }

    /**
     * Dashboard search queries and StroomQL queries will run on specific nodes so find out which node that is.
     *
     * @param nodeName The provided search node.
     * @param request  The ask stroom AI request.
     * @return The node to use or null if the current node is ok.
     */
    public String getBestNode(final String nodeName, final AskStroomAiRequest request) {
        if (request.getData() instanceof final DashboardTableData dashboardTableData) {
            return dashboardService.getBestNode(nodeName, dashboardTableData.getSearchRequest());
        } else if (request.getData() instanceof final QueryTableData queryTableData) {
            return queryService.getBestNode(nodeName, queryTableData.getSearchRequest());
        }
        return null;
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    public AskStroomAiResponse askStroomAi(final AskStroomAiRequest request) {
        if (request.getData() instanceof final DashboardTableData dashboardTableData) {
            final Function<OffsetRange, DashboardSearchResponse> dataProvider = range -> {
                DashboardSearchRequest searchRequest = dashboardTableData.getSearchRequest();
                final ComponentResultRequest componentResultRequest = searchRequest.getComponentResultRequests().getFirst();
                if (componentResultRequest instanceof TableResultRequest tableResultRequest) {
                    tableResultRequest = tableResultRequest.copy().requestedRange(range).build();
                    searchRequest = dashboardTableData.getSearchRequest()
                            .copy()
                            .componentResultRequests(Collections.singletonList(tableResultRequest))
                            .build();
                    return dashboardService.search(searchRequest);
                }
                throw new RuntimeException("No table component provided");
            };
            return new AskStroomAiResponse(createStroomAiTableSummary(
                    dashboardTableData.getChatMemoryId(),
                    request.getMessage(),
                    dataProvider));

        } else if (request.getData() instanceof final QueryTableData queryTableData) {
            final Function<OffsetRange, DashboardSearchResponse> dataProvider = range -> {
                final QuerySearchRequest searchRequest = queryTableData
                        .getSearchRequest()
                        .copy()
                        .requestedRange(range)
                        .build();
                return queryService.search(searchRequest);
            };
            return new AskStroomAiResponse(createStroomAiTableSummary(
                    queryTableData.getChatMemoryId(),
                    request.getMessage(),
                    dataProvider));
        } else if (request.getData() instanceof final GeneralTableData generalTableData) {
            return new AskStroomAiResponse(createGeneralAiTableSummary(
                    generalTableData.getChatMemoryId(),
                    request.getMessage(),
                    generalTableData));
        }

        throw new IllegalStateException();
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createGeneralAiTableSummary(final String chatMemoryId,
                                               final String aiQuery,
                                               final GeneralTableData generalTableData) {
        try {
            final OpenAIModelConfig modelConfig = openAiModelConfigProvider.get();
            final ResultBuilder resultBuilder = createResultBuilder(chatMemoryId, aiQuery);

            // Create column header string.
            final String header = writeHeader(generalTableData.getColumns());

            // Batch and summarise user message responses into a combined summary
            final int maxBatchSize = modelConfig.getMaximumBatchSize();
            final int maximumRowCount = modelConfig.getMaximumTableInputRows();
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

        return openAIService.getChatModel(openAIModelDoc.getModelId(),
                openAIModelDoc.getBaseUrl(), openAIModelDoc.getApiKey());
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createStroomAiTableSummary(final String chatMemoryId,
                                              final String aiQuery,
                                              final Function<OffsetRange, DashboardSearchResponse> dataProvider) {
        try {
            final OpenAIModelConfig modelConfig = openAiModelConfigProvider.get();
            final ResultBuilder resultBuilder = createResultBuilder(chatMemoryId, aiQuery);

//            final ResultBuilder2 resultBuilder = new ResultBuilder2(chatModel, aiQuery);

            // Get the first result page from the data source.
            OffsetRange range = new OffsetRange(0, 100);
            DashboardSearchResponse response = dataProvider.apply(range);
            TableResult result = (TableResult) response.getResults().getFirst();

            // Create column header string.
            final String header = writeHeader(result.getColumns().stream().map(Column::getName).toList());

            // Batch and summarise user message responses into a combined summary
            final int maxBatchSize = modelConfig.getMaximumBatchSize();
            final int maximumRowCount = modelConfig.getMaximumTableInputRows();
            final StringBuilder batch = new StringBuilder(header);
            int rowCount = 0;

            while (!NullSafe.isEmptyCollection(result.getRows())) {
                for (final Row row : result.getRows()) {
                    final List<String> rowValues = row.getValues();
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

    private ResultBuilder createResultBuilder(final String chatMemoryId,
                                              final String aiQuery) {
        final OpenAIModelConfig modelConfig = openAiModelConfigProvider.get();
        if (modelConfig == null || modelConfig.getDefaultApiConfig() == null) {
            throw new RuntimeException("No default OpenAI API specified");
        }
        final DocRef docRef = modelConfig.getDefaultApiConfig();

        final ChatModel chatModel = getChatModel(docRef);
        final String tableChatMemoryId = TABLE_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        final String summaryChatMemoryId = SUMMARY_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        final int maxTokens = chatMemoryConfigProvider.get().getTokenLimit();

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
}
