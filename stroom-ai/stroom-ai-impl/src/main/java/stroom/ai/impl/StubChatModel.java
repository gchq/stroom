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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A ChatModel implementation that returns deterministic, structured responses
 * without any network calls. Designed for end-to-end testing of the batch/merge pipeline.
 * <p>
 * Activated by setting {@code modelId = "__stub__"} on an OpenAIModel document.
 * <p>
 * Simulates realistic latency so that progress messages, THINKING updates,
 * and attachment download status transitions are visible in the UI during testing.
 */
class StubChatModel implements ChatModel {

    /**
     * Simulated latency for data batch analysis calls (per-batch).
     * Deliberately long enough to see THINKING progress messages update.
     */
    private static final int BATCH_LATENCY_MS = 2_000;

    /**
     * Simulated latency for merge / conversational calls.
     */
    private static final int MERGE_LATENCY_MS = 3_000;

    /**
     * Simulated latency for simple conversational (no data) calls.
     */
    private static final int CONVERSATIONAL_LATENCY_MS = 1_500;

    @Override
    public ChatResponse doChat(final ChatRequest chatRequest) {
        // Extract the user's query from the last UserMessage.
        final String userContent = chatRequest.messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second)
                .orElse("(no query)");

        // Detect whether this is a merge prompt or a data analysis prompt.
        if (userContent.contains("SUMMARY A:") || userContent.contains("Summary A:")
                || userContent.contains("summary A:")) {
            // Merge step — simulate longer thinking for combining batch results.
            sleep(MERGE_LATENCY_MS);
            return buildStubResponse("[Stub Merged Summary]\n\n"
                    + "Combined analysis from multiple batches.\n"
                    + "- Total patterns identified: 3\n"
                    + "- Key trend: consistent activity across time window\n"
                    + "- Anomalies: none detected in stub mode");
        }

        // Count approximate data rows (pipe-delimited markdown table rows).
        // Trim each line before checking — the header row may inherit leading whitespace
        // from the prompt template's {{table}} placeholder position.
        long dataRows = userContent.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("|") && !line.contains("---"))
                .count();
        // Subtract header row.
        dataRows = Math.max(0, dataRows - 1);

        if (dataRows > 0) {
            // Data batch — simulate per-batch analysis time.
            sleep(BATCH_LATENCY_MS);
        } else {
            // Pure conversation — no table data.
            sleep(CONVERSATIONAL_LATENCY_MS);
        }

        return buildStubResponse("[Stub Analysis --- " + dataRows + " rows]\n\n"
                + "**Query**: " + truncate(extractQuery(userContent), 100) + "\n\n"
                + "**Findings**:\n"
                + "- Processed " + dataRows + " data rows\n"
                + "- Distribution: approximately uniform\n"
                + "- Notable values: column 1 contains diverse entries\n"
                + "- Recommendation: review top 5 entries for outliers\n\n"
                + "*This is a stub response for testing. "
                + "Configure a real model to get actual AI analysis.*");
    }

    private static void sleep(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ChatResponse buildStubResponse(final String text) {
        return ChatResponse.builder()
                .aiMessage(new AiMessage(text))
                .build();
    }

    private String extractQuery(final String content) {
        // Try to find the query between "USER QUERY:" and "DATA TABLE:" markers.
        final int queryStart = content.indexOf("USER QUERY:");
        final int queryEnd = content.indexOf("DATA TABLE:");
        if (queryStart >= 0 && queryEnd > queryStart) {
            return content.substring(queryStart + 11, queryEnd).trim();
        }
        return content.substring(0, Math.min(content.length(), 200));
    }

    private String truncate(final String s, final int max) {
        return s.length() <= max
                ? s
                : s.substring(0, max) + "...";
    }
}
