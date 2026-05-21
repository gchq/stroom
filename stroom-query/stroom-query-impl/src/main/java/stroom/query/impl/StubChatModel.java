package stroom.query.impl;

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
 */
class StubChatModel implements ChatModel {

    private static final int SIMULATED_LATENCY_MS = 500;

    @Override
    public ChatResponse doChat(final ChatRequest chatRequest) {
        // Simulate LLM processing time so progress messages are visible.
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Extract the user's query from the last UserMessage.
        final String userContent = chatRequest.messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second)
                .orElse("(no query)");

        // Detect whether this is a merge prompt or a data analysis prompt.
        if (userContent.contains("SUMMARY A:") || userContent.contains("Summary A:")
                || userContent.contains("summary A:")) {
            return buildStubResponse("[Stub Merged Summary]\n\n"
                    + "Combined analysis from multiple batches.\n"
                    + "- Total patterns identified: 3\n"
                    + "- Key trend: consistent activity across time window\n"
                    + "- Anomalies: none detected in stub mode");
        }

        // Count approximate data rows (pipe-delimited markdown table rows).
        long dataRows = userContent.lines()
                .filter(line -> line.startsWith("|") && !line.contains("---"))
                .count();
        // Subtract header row.
        dataRows = Math.max(0, dataRows - 1);

        return buildStubResponse("[Stub Analysis \u2014 " + dataRows + " rows]\n\n"
                + "**Query**: " + truncate(extractQuery(userContent), 100) + "\n\n"
                + "**Findings**:\n"
                + "- Processed " + dataRows + " data rows\n"
                + "- Distribution: approximately uniform\n"
                + "- Notable values: column 1 contains diverse entries\n"
                + "- Recommendation: review top 5 entries for outliers\n\n"
                + "*This is a stub response for testing. "
                + "Configure a real model to get actual AI analysis.*");
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
                : s.substring(0, max) + "\u2026";
    }
}
