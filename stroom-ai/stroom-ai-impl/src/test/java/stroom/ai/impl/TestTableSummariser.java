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

import stroom.ai.api.TableSource;
import stroom.ai.api.TableSummaryProgressListener;
import stroom.ai.api.TableSummaryRequest;
import stroom.ai.api.TableSummaryResult;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.task.api.ExecutorProvider;
import stroom.task.shared.ThreadPool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers splitting a table into batches and assembling one answer from them. The point of batch analysis
 * is that an answer survives being assembled from parts, so a part that fails must cost its own rows and
 * nothing else, and the answer must say what it does not cover.
 */
class TestTableSummariser {

    @TempDir
    private Path tempDir;

    /**
     * Two rows to a batch, so a handful of rows is several batches.
     */
    private final TableAnalysisConfig config = new TableAnalysisConfig(100, 2, 2, null, null, null);

    private final TableSummariserImpl summariser = new TableSummariserImpl(new ExecutorProvider() {
        @Override
        public Executor get() {
            // Same thread, so a test asserting on what the model was asked does not race the assertion.
            return Runnable::run;
        }

        @Override
        public Executor get(final ThreadPool threadPool) {
            return get();
        }
    }, () -> null);

    // -----------------------------------------------------------------------------------------------
    // Batching
    // -----------------------------------------------------------------------------------------------

    @Test
    void rows_areSplitIntoBatchesThatEachCarryTheHeader() {
        final List<String> batches = summariser.buildBatchesFromMarkdown(table(5), config);

        assertThat(batches).hasSize(3);
        assertThat(batches).allSatisfy(batch ->
                assertThat(batch).startsWith("| Name | Count |\n| --- | --- |\n"));
        assertThat(batches.get(0)).contains("row1").contains("row2").doesNotContain("row3");
        assertThat(batches.get(1)).contains("row3").contains("row4").doesNotContain("row5");
        assertThat(batches.get(2)).contains("row5");
    }

    @Test
    void tableWithNoRows_producesNoBatches() {
        assertThat(summariser.buildBatchesFromMarkdown(table(0), config)).isEmpty();
    }

    @Test
    void fileWithoutHeaderAndSeparator_producesNoBatches() {
        assertThat(summariser.buildBatchesFromMarkdown(write(""), config)).isEmpty();
        assertThat(summariser.buildBatchesFromMarkdown(write("| Name | Count |\n"), config)).isEmpty();
    }

    @Test
    void blankLines_areNotCountedAsRows() {
        final List<String> batches = summariser.buildBatchesFromMarkdown(
                write("| Name | Count |\n| --- | --- |\n\n| row1 | 1 |\n\n| row2 | 2 |\n"), config);

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst()).contains("row1").contains("row2");
    }

    // -----------------------------------------------------------------------------------------------
    // Summarising
    // -----------------------------------------------------------------------------------------------

    @Test
    void oneBatch_isAnsweredWithoutMergingOrNotes() {
        final TableSummaryResult result = summarise(table(2), prompt -> "THE ANSWER");

        // Nothing was lost, so there is nothing to tell the reader and no merge to do.
        assertThat(result.text()).isEqualTo("THE ANSWER");
        assertThat(result.summarised()).isTrue();
    }

    @Test
    void everyBatch_isPutToTheModel() {
        final List<String> prompts = Collections.synchronizedList(new ArrayList<>());

        summarise(table(5), prompt -> {
            prompts.add(prompt);
            return "summary of " + prompts.size();
        });

        // Three batch calls plus the merge of what they produced.
        assertThat(prompts).hasSize(4);
        assertThat(prompts.subList(0, 3)).anySatisfy(prompt -> assertThat(prompt).contains("row1"));
        assertThat(prompts.subList(0, 3)).anySatisfy(prompt -> assertThat(prompt).contains("row5"));
        assertThat(prompts.getLast()).contains("--- Summary 1 ---");
    }

    @Test
    void failedBatch_costsItsOwnRowsAndIsDeclared() {
        final TableSummaryResult result = summarise(table(5), prompt -> {
            if (prompt.contains("row3")) {
                throw new RuntimeException("model said no");
            }
            return prompt.contains("--- Summary ")
                    ? "MERGED"
                    : "a finding";
        });

        // Some batches contributed, so this is still a summary - it just says what it does not cover.
        assertThat(result.summarised()).isTrue();
        assertThat(result.text()).contains("MERGED");
        assertThat(result.text()).contains("This answer covers 2 of 3 batches of the data, as 1 batch failed");
    }

    @Test
    void truncatedSource_tellsTheModelThatThereWasMoreData() {
        final List<String> prompts = Collections.synchronizedList(new ArrayList<>());

        summariser.summarise(request(new TableSource("truncated", table(2), true), prompt -> {
            prompts.add(prompt);
            return "answer";
        }).build());

        assertThat(prompts.getFirst()).contains("truncated to the first 100 rows of a larger result set");
    }

    @Test
    void noData_saysSoRatherThanAnsweringEmpty() {
        final TableSummaryResult result = summarise(table(0), prompt -> {
            throw new AssertionError("The model should not be called when there is no data");
        });

        // Said, but not as a summary - a caller decorating a report must not present this as one.
        assertThat(result.summarised()).isFalse();
        assertThat(result.text()).isEqualTo("No data available for analysis.");
    }

    @Test
    void sourceThatCannotBeRead_isReportedRatherThanFailingTheCall() {
        final TableSummaryResult result = summarise(tempDir.resolve("gone.md"), prompt -> {
            throw new AssertionError("The model should not be called when there is no data");
        });

        assertThat(result.summarised()).isFalse();
        assertThat(result.text()).isEqualTo("The data could not be read. It may have been cleaned up.");
    }

    @Test
    void cancellingBeforeAnyBatchRuns_saysSo() {
        final TableSummaryRequest request = request(
                new TableSource("test", table(5), false),
                prompt -> {
                    throw new AssertionError("The model should not be called once cancelled");
                })
                .cancelled(() -> true)
                .build();

        final TableSummaryResult result = summariser.summarise(request);
        assertThat(result.summarised()).isFalse();
        assertThat(result.text()).isEqualTo("Analysis was cancelled before any results were produced.");
    }

    @Test
    void notesTheCallerSeeds_areKeptAlongsideTheCoverageNote() {
        final AnswerNotes notes = new AnswerNotes().add("Something the caller knows");

        final TableSummaryResult result = summariser.summarise(
                request(new TableSource("test", table(5), false), prompt -> {
                    if (prompt.contains("row3")) {
                        throw new RuntimeException("model said no");
                    }
                    return "a finding";
                }).build(),
                notes,
                null,
                null);

        assertThat(result.text()).contains("Something the caller knows");
        assertThat(result.text()).contains("This answer covers 2 of 3 batches");
    }

    @Test
    void progress_isReportedAsTheBatchesAreWorkedThrough() {
        final List<String> progress = Collections.synchronizedList(new ArrayList<>());

        summariser.summarise(request(new TableSource("test", table(5), false), prompt -> "a finding")
                .progressListener(new TableSummaryProgressListener() {
                    @Override
                    public void onBatchesBuilt(final int batchCount, final int sourceCount) {
                        progress.add("built " + batchCount + " from " + sourceCount);
                    }

                    @Override
                    public void onBatchStarted(final int batchNumber, final int batchCount) {
                        progress.add("started " + batchNumber + "/" + batchCount);
                    }
                })
                .build());

        assertThat(progress).containsExactly(
                "built 3 from 1",
                "started 1/3",
                "started 2/3",
                "started 3/3");
    }

    /**
     * A cancellation part way through is a stop on the work still queued, not a discarding of what has
     * already been found, so the answer still covers the batches that completed.
     */
    @Test
    void cancellingPartWayThrough_stillAnswersFromWhatWasFound() {
        final AtomicBoolean cancelled = new AtomicBoolean();

        final TableSummaryResult result = summariser.summarise(
                request(new TableSource("test", table(5), false), prompt -> {
                    // Stop after the first batch, as a user pressing stop would.
                    cancelled.set(true);
                    return "a finding";
                })
                        .cancelled(cancelled::get)
                        .build());

        assertThat(result.summarised()).isTrue();
        assertThat(result.text()).contains("a finding");
        assertThat(result.text()).contains("This answer covers 1 of 3 batches of the data, "
                                           + "as the analysis was cancelled before the rest were processed");
    }

    // -----------------------------------------------------------------------------------------------

    private TableSummaryResult summarise(final Path markdownFile, final Function<String, String> answer) {
        return summariser.summarise(request(new TableSource("test", markdownFile, false), answer).build());
    }

    private TableSummaryRequest.Builder request(final TableSource source,
                                                final Function<String, String> answer) {
        return TableSummaryRequest
                .builder()
                .source(source)
                .chatModel(chatModel(answer))
                .config(config)
                .query("What is going on?");
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

    /**
     * @return A markdown table with the given number of data rows.
     */
    private Path table(final int rowCount) {
        final StringBuilder sb = new StringBuilder("| Name | Count |\n| --- | --- |\n");
        IntStream.rangeClosed(1, rowCount).forEach(i ->
                sb.append("| row").append(i).append(" | ").append(i).append(" |\n"));
        return write(sb.toString());
    }

    private Path write(final String content) {
        try {
            final Path file = Files.createTempFile(tempDir, "table", ".md");
            Files.writeString(file, content);
            return file;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
