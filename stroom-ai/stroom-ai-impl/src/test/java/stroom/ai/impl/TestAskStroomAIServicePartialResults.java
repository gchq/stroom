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

import stroom.ai.shared.TableAnalysisConfig;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what happens to results that have already been produced when the merge step hits a limit. The
 * point of batch analysis is that an answer survives being assembled from parts, so no failure in the
 * assembly should lose a part.
 */
class TestAskStroomAIServicePartialResults {

    private final AskStroomAIService service = new AskStroomAIService(
            null, null, null, null, null, null, null, null, null, null, null);
    private final TableAnalysisConfig config = new TableAnalysisConfig();

    @Test
    void mergeFailure_keepsEverySummary() {
        final List<String> summaries = summaries(3);

        final String result = service.mergeAllSummaries(
                chatModel(prompt -> {
                    throw new RuntimeException("maximum context length exceeded");
                }),
                summaries,
                config,
                null);

        assertThat(result).contains(summaries);
        assertThat(result).contains("could not be condensed into a single summary");
    }

    @Test
    void mergeFailureOfOneChunk_keepsThatChunkAndMergesTheRest() {
        // 12 summaries is a chunk of 10 and a chunk of 2. Fail only the larger one.
        final List<String> summaries = summaries(12);

        final String result = service.mergeAllSummaries(
                chatModel(prompt -> {
                    if (prompt.contains("finding 10")) {
                        throw new RuntimeException("maximum context length exceeded");
                    }
                    return "MERGED";
                }),
                summaries,
                config,
                null);

        // The chunk that could not be merged is kept as it was, the other is represented by its merge.
        assertThat(result).contains(summaries.subList(0, 10));
        assertThat(result).contains("MERGED");
    }

    @Test
    void manySummaries_areMergedInBoundedChunks() {
        // Without chunking this would be a single call holding all 25 summaries.
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger mostSummariesInOneCall = new AtomicInteger();

        final String result = service.mergeAllSummaries(
                chatModel(prompt -> {
                    calls.incrementAndGet();
                    mostSummariesInOneCall.accumulateAndGet(count(prompt, "--- Summary "), Math::max);
                    return "MERGED";
                }),
                summaries(25),
                config,
                null);

        assertThat(result).isEqualTo("MERGED");
        assertThat(mostSummariesInOneCall).hasValueLessThanOrEqualTo(10);
        assertThat(calls).hasValueGreaterThan(1);
    }

    @Test
    void singleSummary_isReturnedAsIs() {
        final String result = service.mergeAllSummaries(
                chatModel(prompt -> {
                    throw new AssertionError("The model should not be called to merge one summary");
                }),
                List.of("only one"),
                config,
                null);

        assertThat(result).isEqualTo("only one");
    }

    @Test
    void coverageIsOnlyNotedWhenIncomplete() {
        assertThat(service.describeCoverage(4, 4, 0, 0, false)).isEmpty();
        assertThat(service.describeCoverage(3, 4, 1, 0, false))
                .contains("3 of 4 batches")
                .contains("1 batch failed");
        assertThat(service.describeCoverage(2, 4, 2, 0, false)).contains("2 batches failed");
        assertThat(service.describeCoverage(1, 4, 0, 0, true)).contains("cancelled");
    }

    @Test
    void unreadableAttachmentsAreNotedEvenWhenEveryBatchRan() {
        // Batches are only built from the attachments that could be read, so the batch count alone
        // would say the answer was complete.
        assertThat(service.describeCoverage(4, 4, 0, 1, false))
                .contains("1 attachment could not be read");
        assertThat(service.describeCoverage(3, 4, 1, 2, false))
                .contains("3 of 4 batches")
                .contains("2 attachments could not be read");
    }

    private List<String> summaries(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "finding " + i)
                .toList();
    }

    /**
     * A model that answers from the text of the last user message. {@link ChatModel} has no abstract
     * method, so this cannot be a lambda.
     */
    private ChatModel chatModel(final Function<String, String> answer) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                final UserMessage lastMessage = (UserMessage) chatRequest.messages().getLast();
                return ChatResponse.builder()
                        .aiMessage(new AiMessage(answer.apply(lastMessage.singleText())))
                        .build();
            }
        };
    }

    private int count(final String text, final String token) {
        int count = 0;
        int idx = text.indexOf(token);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(token, idx + token.length());
        }
        return count;
    }
}
