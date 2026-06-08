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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * A ChatModel implementation that returns deterministic, structured responses
 * without any network calls. Designed for end-to-end testing of the unified
 * single-call processing pipeline and batch fallback.
 * <p>
 * Activated by setting {@code modelId = "__stub__"} on an OpenAIModel document.
 * <p>
 * Simulates realistic latency so that progress messages, WORKING updates,
 * and attachment download status transitions are visible in the UI during testing.
 * <p>
 * Detects three call patterns:
 * <ul>
 *     <li><b>Unified</b> — multi-turn messages with {@code [Attached Table: ...]} tags
 *         and/or plain conversation (the primary path).</li>
 *     <li><b>Batch</b> — single-shot calls containing {@code USER QUERY:} and
 *         {@code DATA TABLE:} markers from the batch fallback template.</li>
 *     <li><b>Merge</b> — calls containing {@code --- Summary} markers from the
 *         multi-summary merge step.</li>
 * </ul>
 */
class StubChatModel implements ChatModel {

    /**
     * Simulated latency for unified calls (with or without attachments).
     */
    private static final int UNIFIED_LATENCY_MS = 1_500;

    /**
     * Simulated latency for batch analysis calls (per-batch).
     * Deliberately long enough to see WORKING progress messages update.
     */
    private static final int BATCH_LATENCY_MS = 2_000;

    /**
     * Simulated latency for merge calls.
     */
    private static final int MERGE_LATENCY_MS = 3_000;

    /**
     * Character count threshold above which the stub simulates a context overflow error.
     * Set low enough that a conversation with a large attachment will trigger it,
     * but high enough that a trimmed conversation will succeed.
     */
    private static final int STUB_CONTEXT_OVERFLOW_THRESHOLD = 50_000;

    @Override
    public ChatResponse doChat(final ChatRequest chatRequest) {
        // Collect all user messages to detect the call pattern.
        final List<String> userTexts = new ArrayList<>();
        for (final ChatMessage message : chatRequest.messages()) {
            if (message instanceof final UserMessage userMessage) {
                userTexts.add(userMessage.singleText());
            }
        }

        final String lastUserText = userTexts.isEmpty()
                ? "(no query)"
                : userTexts.getLast();

        // Simulate context overflow for testing the progressive trim retry loop.
        // Internal calls (merge, batch analysis, summarisation) are exempt.
        if (!isInternalCall(lastUserText)) {
            int totalChars = 0;
            for (final ChatMessage message : chatRequest.messages()) {
                if (message instanceof final UserMessage userMessage) {
                    totalChars += userMessage.singleText().length();
                }
            }
            if (totalChars > STUB_CONTEXT_OVERFLOW_THRESHOLD) {
                throw new RuntimeException(
                        "context_length_exceeded: input too long (stub simulation, "
                        + totalChars + " chars > " + STUB_CONTEXT_OVERFLOW_THRESHOLD + " limit)");
            }
        }

        // 1. Merge call — batch fallback is combining partial summaries.
        if (lastUserText.contains("--- Summary ")) {
            sleep(MERGE_LATENCY_MS);
            return buildStubResponse("[Stub Merged Summary]\n\n" +
                                     "Combined analysis from multiple batches.\n" +
                                     "- Total patterns identified: 3\n" +
                                     "- Key trend: consistent activity across time window\n" +
                                     "- Anomalies: none detected in stub mode");
        }

        // 2. Batch fallback call — uses the template with USER QUERY: / DATA TABLE: markers.
        if (lastUserText.contains("USER QUERY:") && lastUserText.contains("DATA TABLE:")) {
            final long dataRows = countMarkdownDataRows(lastUserText);
            final String query = extractBetween(lastUserText, "USER QUERY:", "DATA TABLE:");
            sleep(BATCH_LATENCY_MS);
            return buildStubResponse("[Stub Batch Analysis — " + dataRows + " rows]\n\n"
                                     + "**Query**: " + truncate(query, 100) + "\n\n"
                                     + "**Findings**:\n"
                                     + "- Processed " + dataRows + " data rows\n"
                                     + "- Distribution: approximately uniform\n"
                                     + "- Recommendation: review top 5 entries for outliers\n\n"
                                     + "*This is a stub batch response for testing.*");
        }

        // 3. Unified call — the primary path. Attachment data appears as
        //    [Attached Table: ...] tagged UserMessages in earlier turns.
        final List<String> attachmentNames = new ArrayList<>();
        long totalDataRows = 0;
        for (final String text : userTexts) {
            if (text.startsWith("[Attached Table:")) {
                // Extract the table description from the tag.
                final int closeBracket = text.indexOf(']');
                if (closeBracket > 0) {
                    attachmentNames.add(text.substring(16, closeBracket).trim());
                }
                totalDataRows += countMarkdownDataRows(text);
            }
        }

        sleep(UNIFIED_LATENCY_MS);

        if (attachmentNames.isEmpty()) {
            // Pure conversation — no attachments.
            return buildStubResponse("[Stub Response]\n\n"
                                     + "You asked: " + truncate(lastUserText, 150) + "\n\n"
                                     + "This is a stub conversational response for testing. "
                                     + "Configure a real model to get actual AI analysis.");
        }

        // Unified call with attachment data.
        final StringBuilder sb = new StringBuilder();
        sb.append("[Stub Unified Analysis — ")
                .append(attachmentNames.size())
                .append(" table(s), ")
                .append(totalDataRows)
                .append(" total rows]\n\n");
        sb.append("**Query**: ").append(truncate(lastUserText, 100)).append("\n\n");
        sb.append("**Tables analysed**:\n");
        for (final String name : attachmentNames) {
            sb.append("- ").append(name).append('\n');
        }
        sb.append("\n**Findings**:\n")
                .append("- Processed ").append(totalDataRows).append(" data rows across ")
                .append(attachmentNames.size()).append(" table(s)\n")
                .append("- Distribution: approximately uniform\n")
                .append("- Notable values: column 1 contains diverse entries\n")
                .append("- Recommendation: review top 5 entries for outliers\n\n")
                .append("*This is a stub response for testing. ")
                .append("Configure a real model to get actual AI analysis.*");
        return buildStubResponse(sb.toString());
    }

    /**
     * Counts the number of markdown data rows (pipe-delimited lines,
     * excluding the header and separator rows).
     */
    private long countMarkdownDataRows(final String text) {
        final long tableLines = text.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("|") && !line.contains("---"))
                .count();
        // Subtract the header row (if present).
        return Math.max(0, tableLines - 1);
    }

    /**
     * Extracts the text between two markers, trimmed.
     */
    private String extractBetween(final String content,
                                  final String startMarker,
                                  final String endMarker) {
        final int start = content.indexOf(startMarker);
        final int end = content.indexOf(endMarker);
        if (start >= 0 && end > start) {
            return content.substring(start + startMarker.length(), end).trim();
        }
        return content.substring(0, Math.min(content.length(), 200));
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

    private String truncate(final String s, final int max) {
        return s.length() <= max
                ? s
                : s.substring(0, max) + "...";
    }

    /**
     * Returns true if the call is an internal operation (merge, batch analysis,
     * or summarisation) that should be exempt from overflow simulation.
     */
    private boolean isInternalCall(final String lastUserText) {
        return lastUserText.contains("--- Summary ")
               || lastUserText.contains("USER QUERY:")
               || lastUserText.contains("Summarise the following")
               || lastUserText.contains("Additional conversation to summarise");
    }
}
