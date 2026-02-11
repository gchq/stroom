package stroom.langchain.impl;

import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.ChatMemoryConfig;
import stroom.langchain.api.ChatMemoryService;
import stroom.langchain.api.OpenAIService;
import stroom.langchain.api.SimpleTokenCountEstimator;
import stroom.langchain.api.SummaryReducer;
import stroom.langchain.api.TableQuery;
import stroom.langchain.api.TableQueryMessages;
import stroom.langchain.api.TableSummaryMessages;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.date.DateUtil;

import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Disabled // Disabled because an API key must be configured to run this test
public class TestAskStroomAi {

    private static final String TABLE_CHAT_MEMORY_KEY = "table";
    private static final String SUMMARY_CHAT_MEMORY_KEY = "summary";
    private static final Pattern MD_TABLE_ESCAPE = Pattern.compile("[$&`*_~#+-.!|()\\[\\]{}<>]");

    @Test
    void test() {
//        final String table = """
//                | EventTime | UserId | Count |
//                | --- | --- | --- |
//                | 2010 01 01T00 00 00 000Z | user4 | 5 |
//                | 2010 01 01T00 05 00 000Z | user3 | 12 |
//                | 2010 01 01T00 06 00 000Z | user1 | 6 |
//                | 2010 01 01T00 04 00 000Z | user7 | 9 |
//                | 2010 01 01T00 06 00 000Z | user7 | 365 |
//                | 2010 01 01T00 04 00 000Z | user2 | 7 |
//                | 2010 01 01T00 04 00 000Z | user4 | 4 |
//                | 2010 01 01T00 02 00 000Z | user10 | 2 |
//                | 2010 01 01T00 01 00 000Z | user5 | 8 |
//                | 2010 01 01T00 03 00 000Z | user2 | 4 |
//                | 2010 01 01T00 08 00 000Z | user3 | 7 |
//                | 2010 01 01T00 00 00 000Z | user1 | 362 |
//                | 2010 01 01T00 08 00 000Z | user9 | 369 |
//                | 2010 01 01T00 08 00 000Z | user5 | 5 |
//                | 2010 01 01T00 01 00 000Z | user10 | 4 |
//                | 2010 01 01T00 07 00 000Z | user8 | 375 |
//                | 2010 01 01T00 04 00 000Z | user5 | 365 |
//                | 2010 01 01T00 01 00 000Z | user1 | 8 |
//                | 2010 01 01T00 09 00 000Z | user10 | 368 |
//
//                """;


//        final String table = """
//                | EventTime                | UserId | Count |
//                | ------------------------ | ------ | ----- |
//                | 2010 01 01T00 00 00 000Z | user4  | 5     |
//                | 2010 01 01T00 05 00 000Z | user3  | 12    |
//                | 2010 01 01T00 06 00 000Z | user1  | 6     |
//                | 2010 01 01T00 04 00 000Z | user7  | 9     |
//                | 2010 01 01T00 06 00 000Z | user7  | 365   |
//                | 2010 01 01T00 04 00 000Z | user2  | 7     |
//                | 2010 01 01T00 04 00 000Z | user4  | 4     |
//                | 2010 01 01T00 02 00 000Z | user10 | 2     |
//                | 2010 01 01T00 01 00 000Z | user5  | 8     |
//                | 2010 01 01T00 03 00 000Z | user2  | 4     |
//                | 2010 01 01T00 08 00 000Z | user3  | 7     |
//                | 2010 01 01T00 00 00 000Z | user1  | 362   |
//                | 2010 01 01T00 08 00 000Z | user9  | 369   |
//                | 2010 01 01T00 08 00 000Z | user5  | 5     |
//                | 2010 01 01T00 01 00 000Z | user10 | 4     |
//                | 2010 01 01T00 07 00 000Z | user8  | 375   |
//                | 2010 01 01T00 04 00 000Z | user5  | 365   |
//                | 2010 01 01T00 01 00 000Z | user1  | 8     |
//                | 2010 01 01T00 09 00 000Z | user10 | 368   |
//
//                """;
//
//                final String table = """
//                | EventTime | UserId | Count |
//                | --- | --- | --- |
//                | 2010 01 01T00 00 00 000Z | user4 | 5 |
//                | 2010 01 01T00 05 00 000Z | user3 | 12 |
//                | 2010 01 01T00 06 00 000Z | user1 | 6 |
//                | 2010 01 01T00 04 00 000Z | user7 | 9 |
//                | 2010 01 01T00 06 00 000Z | user7 | 365 |
//                | 2010 01 01T00 04 00 000Z | user2 | 7 |
//                | 2010 01 01T00 04 00 000Z | user4 | 4 |
//                | 2010 01 01T00 02 00 000Z | user10 | 2 |
//                | 2010 01 01T00 01 00 000Z | user5 | 8 |
//                | 2010 01 01T00 03 00 000Z | user2 | 4 |
//                | 2010 01 01T00 08 00 000Z | user3 | 7 |
//                | 2010 01 01T00 00 00 000Z | user1 | 362 |
//                | 2010 01 01T00 08 00 000Z | user9 | 369 |
//                | 2010 01 01T00 08 00 000Z | user5 | 5 |
//                | 2010 01 01T00 01 00 000Z | user10 | 4 |
//                | 2010 01 01T00 07 00 000Z | user8 | 375 |
//                | 2010 01 01T00 04 00 000Z | user5 | 365 |
//                | 2010 01 01T00 01 00 000Z | user1 | 8 |
//                | 2010 01 01T00 09 00 000Z | user10 | 368 |
//                """;


//        final String table = """
//                | UserId | Count |
//                | --- | --- |
//                | user4 | 5 |
//                | user3 | 12 |
//                """;


        final List<String> columns = List.of("EventTime", "UserId", "Count");
        final int totalRows = 10000;
        final Provider<List<String>> rowCreator = () -> List.of(
                DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                "user" + (int) (Math.random() * 100),
                "" + (int) (Math.random() * 100));

        final String baseUrl = "https://api.openai.com/v1/";
        final String apiKey = System.getProperty("OPEN_API_TEST_KEY");
        final String modelId = "gpt-4o";

        final OpenAIService openAIService = new OpenAIServiceImpl(
                null,
                null,
                null,
                null);
        final OpenAIModelDoc modelDoc = new OpenAIModelDoc(
                UUID.randomUUID().toString(),
                "test",
                "1",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                "test",
                "test",
                null,
                baseUrl,
                apiKey,
                modelId,
                1000,
                null);
        final ChatModel chatModel = openAIService.getChatModel(modelDoc);

        final String chatMemoryId = UUID.randomUUID().toString();
        final String tableChatMemoryId = TABLE_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        final String summaryChatMemoryId = SUMMARY_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        final int maxTokens = 1024;

        final TokenCountEstimator tokenCountEstimator = new SimpleTokenCountEstimator();

        final AskStroomAIConfig modelConfig = new AskStroomAIConfig();
        final ChatMemoryService chatMemoryService = new ChatMemoryServiceImpl(ChatMemoryConfig::new);
        final TableQuery tableQueryService = AiServices.builder(TableQuery.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> TokenWindowChatMemory.builder()
                        .chatMemoryStore(chatMemoryService.getChatMemoryStore())
                        .id(tableChatMemoryId)
                        .maxTokens(maxTokens, tokenCountEstimator)
                        .build())
                .build();
        final SummaryReducer summaryReducerService = AiServices.builder(SummaryReducer.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> TokenWindowChatMemory.builder()
                        .chatMemoryStore(chatMemoryService.getChatMemoryStore())
                        .id(summaryChatMemoryId)
                        .maxTokens(maxTokens, tokenCountEstimator)
                        .build())
                .build();

//        final String batchAnswer = tableQueryService.answerChunk(
//                tableChatMemoryId, "Explain table", table);
//
//        System.out.println(batchAnswer);
//
//
        final ResultBuilder resultBuilder =
                new ResultBuilder(chatModel, chatMemoryId, "Explain table", tableQueryService, summaryReducerService);
////        resultBuilder.add(data);
////        System.out.println(resultBuilder.get());


        // Create column header string.
        final String header = writeHeader(columns);

        // Batch and summarise user message responses into a combined summary
        final int maxBatchSize = modelConfig.getTableSummary().getMaximumBatchSize();
        final int maximumRowCount = modelConfig.getTableSummary().getMaximumTableInputRows();
        final StringBuilder batch = new StringBuilder(header);
        int rowCount = 0;

        for (int i = 0; i < totalRows; i++) {
            final List<String> rowValues = rowCreator.get();
            final String rowString = writeRow(rowValues);

            final int newBatchSize = SummaryReducer.USER_MESSAGE.length() + batch.length() + rowString.length();
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

        System.out.println(resultBuilder.get());
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

        private final ChatModel chatModel;
        private final String aiQuery;
        private final TableQuery tableQuery;
        private final SummaryReducer summaryReducer;
        private final String tableChatMemoryId;
        private final String summaryChatMemoryId;
        private String cumulativeSummary = "";

        public ResultBuilder(final ChatModel chatModel,
                             final String chatMemoryId,
                             final String aiQuery,
                             final TableQuery tableQuery,
                             final SummaryReducer summaryReducer) {
            this.chatModel = chatModel;
            this.aiQuery = aiQuery;
            this.tableQuery = tableQuery;
            this.summaryReducer = summaryReducer;
            tableChatMemoryId = TABLE_CHAT_MEMORY_KEY + "/" + chatMemoryId;
            summaryChatMemoryId = SUMMARY_CHAT_MEMORY_KEY + "/" + chatMemoryId;
        }

        void add(final String data) {
//                        final String batchAnswer = tableQuery.answerChunk(
//                    tableChatMemoryId, aiQuery, data);
//            if (cumulativeSummary.isEmpty()) {
//                cumulativeSummary = batchAnswer;
//            } else {
//                cumulativeSummary = summaryReducer.merge(
//                        summaryChatMemoryId, cumulativeSummary, batchAnswer);
//            }

//            // Process any remaining batch content
//            final List<ChatMessage> messages = new ArrayList<>();
//            messages.add(new SystemMessage("""
//                        You are a data analysis AI. You will answer user questions
//                        using ONLY the markdown-formatted DATA TABLE records provided.
//                        If the records do not contain relevant details, say "No relevant information."
//                    """));
//
//            final String userMessageTemplate = """
//                        USER QUERY:
//                        {{query}}
//
//                        DATA TABLE:
//                        {{table}}
//
//                        Provide findings relevant only to these records, in a concise structured format.
//                    """;
//
//            String message = userMessageTemplate;
//            message = message.replaceAll("\\{\\{query}}", aiQuery);
//            message = message.replaceAll("\\{\\{table}}", data);
//
//            messages.add(new UserMessage(message));
//
            final String batchAnswer = queryTable(data);
            if (cumulativeSummary.isEmpty()) {
                cumulativeSummary = batchAnswer;
            } else {
                cumulativeSummary = merge(cumulativeSummary, batchAnswer);
            }
        }

        private String queryTable(final String table) {
//            // Process any remaining batch content
//            final List<ChatMessage> messages = new ArrayList<>();
//            messages.add(new SystemMessage("""
//                        You are a data analysis AI. You will answer user questions
//                        using ONLY the markdown-formatted DATA TABLE records provided.
//                        If the records do not contain relevant details, say "No relevant information."
//                    """));
//
//            final String userMessageTemplate = """
//                        USER QUERY:
//                        {{query}}
//
//                        DATA TABLE:
//                        {{table}}
//
//                        Provide findings relevant only to these records, in a concise structured format.
//                    """;
//
//            String message = userMessageTemplate;
//            message = message.replaceAll("\\{\\{query}}", aiQuery);
//            message = message.replaceAll("\\{\\{table}}", table);
//
//            messages.add(new UserMessage(message));

            final ChatResponse response = chatModel.chat(TableQueryMessages.createMessages(aiQuery, table));
            return response.aiMessage().text();
        }

        private String merge(final String a, final String b) {
//            final List<ChatMessage> messages = new ArrayList<>();
//            messages.add(new SystemMessage("You merge partial answers into a unified, concise summary."));
//
//            final String userMessageTemplate = """
//                        Merge the following TWO summaries into a single improved summary.
//                        Preserve important details and remove duplicates.
//
//                        SUMMARY A:
//                        {{a}}
//
//                        SUMMARY B:
//                        {{b}}
//                    """;
//
//            String message = userMessageTemplate;
//            message = message.replaceAll("\\{\\{a}}", a);
//            message = message.replaceAll("\\{\\{b}}", b);
//
//            messages.add(new UserMessage(message));

            final ChatResponse response = chatModel.chat(TableSummaryMessages.createMessages(a, b));
            return response.aiMessage().text();
        }

        String get() {
            return cumulativeSummary;
        }
    }
}
