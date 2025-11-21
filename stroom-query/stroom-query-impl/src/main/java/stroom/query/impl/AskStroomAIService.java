package stroom.query.impl;

import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.DashboardTableData;
import stroom.ai.shared.QueryTableData;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.docref.DocRef;
import stroom.langchain.api.ChatMemoryConfig;
import stroom.langchain.api.ChatMemoryService;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.langchain.api.SimpleTokenCountEstimator;
import stroom.langchain.api.SummaryReducer;
import stroom.langchain.api.TableQuery;
import stroom.openai.shared.OpenAIModelConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;

import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class AskStroomAIService {

    private static final String TABLE_CHAT_MEMORY_KEY = "table";
    private static final String SUMMARY_CHAT_MEMORY_KEY = "summary";

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
            final DashboardSearchResponse searchResponse =
                    dashboardService.search(dashboardTableData.getSearchRequest());
            return getQueryTableResponse(request,
                    dashboardTableData.getSearchRequest().getQueryKey().toString(),
                    searchResponse);
        } else if (request.getData() instanceof final QueryTableData queryTableData) {
            final DashboardSearchResponse searchResponse =
                    queryService.search(queryTableData.getSearchRequest());
            return getQueryTableResponse(request,
                    queryTableData.getSearchRequest().getQueryKey().toString(),
                    searchResponse);
        }
        throw new IllegalStateException();
    }

    private AskStroomAiResponse getQueryTableResponse(final AskStroomAiRequest request,
                                                      final String chatMemoryId,
                                                      final DashboardSearchResponse searchResponse) {
        final TableResult result = (TableResult) searchResponse.getResults().getFirst();
        final List<List<String>> rows = result.getRows().stream().map(Row::getValues).toList();
        return new AskStroomAiResponse(createStroomAiTableSummary(
                chatMemoryId,
                request.getMessage(),
                result.getColumns().stream().map(Column::getName).toList(),
                rows.iterator()));
    }

    /**
     * Passes the table rows in batches to the configured LLM chat completion endpoint, along with the user's query.
     * The user is provided with an aggregated response from all batches once compiled.
     */
    private String createStroomAiTableSummary(final String chatMemoryId,
                                              final String aiQuery,
                                              final List<String> columnList,
                                              final Iterator<List<String>> values) {
        final OpenAIModelConfig modelConfig = openAiModelConfigProvider.get();
        if (modelConfig == null || modelConfig.getDefaultApiConfig() == null) {
            throw new RuntimeException("No default OpenAI API specified");
        }

        final DocRef docRef = modelConfig.getDefaultApiConfig();
        if (!OpenAIModelDoc.TYPE.equals(docRef.getType())) {
            throw new RuntimeException("Default OpenAI API doc ref is incorrect");
        }

        final OpenAIModelDoc openAIModelDoc = openAIModelStore.readDocument(docRef);
        if (openAIModelDoc == null) {
            throw new RuntimeException("Default OpenAI API doc cannot be found");
        }

        final ChatModel chatModel = openAIService.getChatModel(openAIModelDoc.getModelId(),
                openAIModelDoc.getBaseUrl(), openAIModelDoc.getApiKey());
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
        final String columns = String.join(" | ", columnList);
        final String columnDiv = columnList.stream().map(col -> "---")
                .collect(Collectors.joining(" | "));

        // Batch and summarise user message responses into a combined summary
        final int maxBatchSize = modelConfig.getMaximumBatchSize();
        final int maximumRowCount = modelConfig.getMaximumTableInputRows();
        final StringBuilder batch = new StringBuilder();
        int batchCount = 0;
        int rowCount = 0;
        final ResultBuilder resultBuilder =
                new ResultBuilder(chatMemoryId, aiQuery, tableQueryService, summaryReducerService);
        while (values.hasNext()) {
            rowCount++;
            batchCount++;
            if (rowCount > maximumRowCount) {
                break;
            }

            final List<String> rowValues = values.next();
            if (batchCount == 1) {
                // Write a new Markdown table header
                batch.append("| ").append(columns).append(" |\n");
                batch.append("| ").append(columnDiv).append(" |\n");
            }
            final StringBuilder row = new StringBuilder();
            if (!rowValues.isEmpty()) {
                rowValues.forEach(cell -> {
                    row.append("| ");
                    if (cell != null) {
                        // Replace characters that can cause issues with Markdown tables
                        row.append(cell
                                .replace("\n", "<br>")
                                .replace('|', ' '));
                    }
                    row.append(" ");
                });
                row.append("|\n");
            }
            final int newBatchSize = SummaryReducer.USER_MESSAGE.length() + batch.length() + row.length();
            if (batchCount > 0 && newBatchSize > maxBatchSize) {
                // Batch message plus the new row would exceed the maximum batch size, so send the batch to the
                // model as-is
                resultBuilder.add(batch.toString());
                batch.setLength(0);
                batchCount = 0;
            } else {
                batch.append(row);
            }
        }

        if (!batch.isEmpty()) {
            // Process any remaining batch content
            resultBuilder.add(batch.toString());
        }

        return resultBuilder.get();
    }

    private static class ResultBuilder {

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
}
