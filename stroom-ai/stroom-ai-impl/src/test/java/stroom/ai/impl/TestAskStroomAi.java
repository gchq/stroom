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
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.TableSummaryConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.date.DateUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Disabled // Disabled because an API key must be configured to run this test
public class TestAskStroomAi {

    private static final Pattern MD_TABLE_ESCAPE = Pattern.compile("[$&`*_~#+-.!|()\\[\\]{}<>]");

    @Test
    void test() {
        final List<String> columns = List.of("EventTime", "UserId", "Count");
        final int totalRows = 10000;

        final String baseUrl = "https://api.openai.com/v1/";
        final String apiKey = System.getProperty("OPEN_API_TEST_KEY");
        final String modelId = "gpt-4o";

        final AiService aiService = new AiServiceImpl(
                null,
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
        final ChatModel chatModel = aiService.getChatModel(modelDoc);

        final AskStroomAIConfig modelConfig = new AskStroomAIConfig();
        final TableSummaryConfig tableSummaryConfig = modelConfig.getTableSummary();
        final ResultBuilder resultBuilder = new ResultBuilder(
                chatModel, "Explain table", tableSummaryConfig);

        // Create column header string.
        final String header = writeHeader(columns);

        // Batch and summarise user message responses into a combined summary
        final int maxBatchSize = tableSummaryConfig.getMaximumBatchSize();
        final int maximumRowCount = tableSummaryConfig.getMaximumTableInputRows();
        final StringBuilder batch = new StringBuilder(header);
        int rowCount = 0;

        for (int i = 0; i < totalRows; i++) {
            final List<String> rowValues = List.of(
                    DateUtil.createNormalDateTimeString(System.currentTimeMillis()),
                    "user" + (int) (Math.random() * 100),
                    "" + (int) (Math.random() * 100));
            final String rowString = writeRow(rowValues);

            final int newBatchSize = batch.length() + rowString.length();
            if (rowCount > 0 && newBatchSize > maxBatchSize) {
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

    /**
     * Uses direct ChatModel.chat() with configurable prompt templates,
     * matching the production ResultBuilder in AskStroomAIService.
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
            final String userPrompt = config.getTableQueryUserPrompt()
                    .replace("{{query}}", aiQuery)
                    .replace("{{table}}", data);

            final List<ChatMessage> messages = new ArrayList<>(2);
            messages.add(new SystemMessage(config.getTableQuerySystemPrompt()));
            messages.add(new UserMessage(userPrompt));

            final ChatResponse response = chatModel.chat(messages);
            final String batchAnswer = response.aiMessage().text();

            if (cumulativeSummary.isEmpty()) {
                cumulativeSummary = batchAnswer;
            } else {
                final String mergePrompt = config.getSummaryMergePrompt()
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
}
