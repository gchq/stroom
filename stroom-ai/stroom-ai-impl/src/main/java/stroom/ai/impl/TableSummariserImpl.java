/*
 * Copyright 2026 Crown Copyright
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
import stroom.ai.api.TableSummariser;
import stroom.ai.api.TableSummaryProgressListener;
import stroom.ai.api.TableSummaryRequest;
import stroom.ai.impl.SummaryMerger.DebugFormatter;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.task.api.ExecutorProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

@Singleton
public class TableSummariserImpl implements TableSummariser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableSummariserImpl.class);

    /**
     * How long a cancelled run waits for batches that are already in flight before giving up on them.
     * The caller is not blocked while this happens, so a batch that is nearly done is worth having, but a
     * stop still has to mean something when the model has stopped responding.
     */
    private static final long CANCELLED_HARVEST_GRACE_MS = 30_000;

    private final ExecutorProvider executorProvider;

    @Inject
    TableSummariserImpl(final ExecutorProvider executorProvider) {
        this.executorProvider = executorProvider;
    }

    @Override
    public String summarise(final TableSummaryRequest request) {
        return summarise(request, new AnswerNotes(), null, null);
    }

    /**
     * As {@link #summarise(TableSummaryRequest)}, for a caller that keeps debug detail of every call it
     * makes to the model, or that has something of its own to tell the reader about the answer.
     *
     * @param notes          Seeded with anything the caller already knows the reader needs to be told, and
     *                       added to here with how much of the data the answer covers.
     * @param debugLog       Where to record each call, or null if the caller is not keeping debug detail.
     * @param debugFormatter Renders a call and its response for that record. Required if debugLog is set.
     */
    String summarise(final TableSummaryRequest request,
                     final AnswerNotes notes,
                     final StringBuilder debugLog,
                     final DebugFormatter debugFormatter) {
        final BooleanSupplier cancelled = request.getCancelled();
        final TableAnalysisConfig tableAnalysisConfig = request.getConfig();
        final TableSummaryProgressListener progressListener = request.getProgressListener();
        final ChatModel chatModel = request.getChatModel();
        final List<TableSource> sources = request.getSources();

        LOGGER.debug(() -> "summarise: sources=" + sources.size()
                           + " maxParallel=" + tableAnalysisConfig.getMaxParallelBatches());

        // Build batches from markdown files on disk.
        final List<String> batches = new ArrayList<>();
        boolean anyTruncated = false;
        int unreadableSources = 0;
        for (final TableSource source : sources) {
            try {
                if (!Files.exists(source.markdownFile())) {
                    throw new RuntimeException("Table data file not found. "
                                               + "Data may have been cleaned up.");
                }
                batches.addAll(buildBatchesFromMarkdown(source.markdownFile(), tableAnalysisConfig));
                if (source.truncated()) {
                    anyTruncated = true;
                }
            } catch (final RuntimeException e) {
                // One source we cannot read should not cost the caller the analysis of the rest.
                unreadableSources++;
                LOGGER.warn(() -> "Skipping unreadable source " + source.description(), e);
            }
        }

        if (batches.isEmpty()) {
            return unreadableSources > 0
                    ? "The attached data could not be read. It may have been cleaned up."
                    : "No data available for analysis.";
        }

        final boolean truncatedData = anyTruncated;
        LOGGER.debug(() -> "summarise: batches=" + batches.size() + " anyTruncated=" + truncatedData);

        // Include truncation note in the user query if applicable.
        final String userQuery = anyTruncated
                ? request.getQuery() + "\n\nNote: this data is truncated to the first "
                  + tableAnalysisConfig.getMaxTotalRows()
                  + " rows of a larger result set."
                : request.getQuery();

        final int totalBatches = batches.size();
        progressListener.onBatchesBuilt(totalBatches, sources.size());

        // Process batches in parallel with bounded concurrency.
        final Executor executor = executorProvider.get();
        final int maxParallel = tableAnalysisConfig.getMaxParallelBatches();
        final Semaphore semaphore = new Semaphore(maxParallel);

        final String systemPrompt = tableAnalysisConfig.getTableQuerySystemPrompt() != null
                ? tableAnalysisConfig.getTableQuerySystemPrompt()
                : TableAnalysisConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT;
        final String userPromptTemplate = tableAnalysisConfig.getTableQueryUserPrompt() != null
                ? tableAnalysisConfig.getTableQueryUserPrompt()
                : TableAnalysisConfig.DEFAULT_TABLE_QUERY_USER_PROMPT;
        final String conversationContext = request.getContext() != null
                ? request.getContext()
                : "";

        if (debugLog != null) {
            debugLog
                    .append("### Batch Fallback (")
                    .append(totalBatches)
                    .append(" batches)\n\n");
        }

        final List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < totalBatches; i++) {
            if (cancelled.getAsBoolean()) {
                final int batchIdx = i;
                LOGGER.debug(() -> "summarise: cancelled at batch " + batchIdx + "/" + totalBatches);
                break;
            }
            final String batch = batches.get(i);
            final int batchNum = i + 1;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for batch slot", e);
                }
                try {
                    if (cancelled.getAsBoolean()) {
                        return null;
                    }
                    progressListener.onBatchStarted(batchNum, totalBatches);

                    final String userPrompt = userPromptTemplate
                            .replace("{{query}}", userQuery)
                            .replace("{{table}}", batch)
                            .replace("{{context}}", conversationContext);

                    LOGGER.trace(() -> "Batch " + batchNum + "/" + totalBatches
                                       + " prompt:\n" + userPrompt);

                    final List<ChatMessage> messages = List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userPrompt));

                    final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                            () -> chatModel.chat(messages),
                            r -> "Batch " + batchNum + "/" + totalBatches
                                 + " responseLength=" + r.aiMessage().text().length());
                    LOGGER.trace(() -> "Batch " + batchNum + "/" + totalBatches
                                       + " response:\n" + response.aiMessage().text());

                    final String responseText = response.aiMessage().text();

                    // Capture batch debug detail (synchronized on debugLog).
                    if (debugLog != null) {
                        synchronized (debugLog) {
                            debugLog
                                    .append("#### Batch ")
                                    .append(batchNum)
                                    .append("/")
                                    .append(totalBatches)
                                    .append("\n\n")
                                    .append(debugFormatter.format(messages, responseText));
                        }
                    }

                    return responseText;
                } finally {
                    semaphore.release();
                }
            }, executor));
        }

        // Once cancelled, give the batches already in flight a short while to land before taking
        // stock. What the caller stopped is the work still queued behind them.
        if (cancelled.getAsBoolean() && futures.stream().anyMatch(future -> !future.isDone())) {
            progressListener.onCancelledAwaitingBatches();
            awaitInFlightBatches(futures);
        }

        // Collect results, handling per-batch failures gracefully. Anything still unfinished after
        // the grace period above is left behind rather than waited on.
        final List<String> summaries = new ArrayList<>();
        int failedBatches = 0;
        for (final CompletableFuture<String> future : futures) {
            if (cancelled.getAsBoolean() && !future.isDone()) {
                continue;
            }
            try {
                final String result = future.join();
                if (result != null && !result.isEmpty()) {
                    summaries.add(result);
                } else if (result != null) {
                    // Answered, but with nothing. That is a batch that did not contribute.
                    failedBatches++;
                }
            } catch (final Exception e) {
                failedBatches++;
                LOGGER.debug(() -> "Batch processing failed", e);
                // Continue collecting results from other batches.
            }
        }

        if (summaries.isEmpty()) {
            if (cancelled.getAsBoolean()) {
                return "Analysis was cancelled before any results were produced.";
            }
            return "No results could be extracted from the data.";
        }

        // Merge summaries.
        LOGGER.debug(() -> "summarise: summaries=" + summaries.size()
                           + (summaries.size() > 1
                ? " -> merging"
                : " -> single result"));

        // A stop means "stop working through the data", not "hand me the fragments". Merging what
        // was found is the answer the caller is waiting for, and its cost is set by how much was
        // produced before the stop, not by how much data is left. If a merge fails, SummaryMerger
        // keeps the summaries rather than losing them.
        final String merged;
        if (summaries.size() == 1) {
            merged = summaries.getFirst();
        } else {
            if (cancelled.getAsBoolean()) {
                progressListener.onCancelledBeforeMerge(summaries.size());
            }
            merged = new SummaryMerger(chatModel, tableAnalysisConfig, debugLog, debugFormatter)
                    .merge(summaries, notes);
        }

        notes.coverage(summaries.size(), totalBatches, failedBatches,
                unreadableSources, cancelled.getAsBoolean());
        return notes.appendTo(merged);
    }

    /**
     * Reads a markdown table file and splits it into batches,
     * each respecting the maximum batch size. The header and separator
     * rows are preserved at the start of each batch.
     */
    List<String> buildBatchesFromMarkdown(final Path mdFile,
                                          final TableAnalysisConfig config) {
        return LOGGER.logDurationIfDebugEnabled(() -> {
            final List<String> batches = new ArrayList<>();
            final int maxRowsPerBatch = config.getMaxRowsPerBatch();

            try (final BufferedReader reader = Files.newBufferedReader(mdFile)) {
                final String headerLine = reader.readLine();
                if (headerLine == null) {
                    return batches;
                }
                final String separatorLine = reader.readLine();
                if (separatorLine == null) {
                    return batches;
                }

                final String mdHeader = headerLine + "\n" + separatorLine + "\n";
                final StringBuilder batch = new StringBuilder(mdHeader);
                int rowsInBatch = 0;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (rowsInBatch >= maxRowsPerBatch && rowsInBatch > 0) {
                        batches.add(batch.toString());
                        batch.setLength(0);
                        batch.append(mdHeader);
                        rowsInBatch = 0;
                    }
                    batch.append(line).append('\n');
                    rowsInBatch++;
                }

                if (rowsInBatch > 0) {
                    batches.add(batch.toString());
                }
            } catch (final IOException e) {
                throw new UncheckedIOException("Failed to read markdown file: " + mdFile, e);
            }
            return batches;
        }, batches -> "buildBatchesFromMarkdown: file=" + mdFile.getFileName()
                      + ", maxRowsPerBatch=" + config.getMaxRowsPerBatch()
                      + ", batch(es)=" + batches.size());
    }

    /**
     * Waits a bounded time for batches that are already in flight, so that a cancelled run reports what
     * they found rather than discarding work that was nearly done.
     */
    private void awaitInFlightBatches(final List<CompletableFuture<String>> futures) {
        try {
            CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .get(CANCELLED_HARVEST_GRACE_MS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final TimeoutException e) {
            LOGGER.debug(() -> "awaitInFlightBatches: batches still running after the grace period, "
                               + "continuing without them");
        } catch (final ExecutionException e) {
            // A batch that failed is accounted for when the results are collected.
            LOGGER.debug(() -> "awaitInFlightBatches: a batch failed", e);
        }
    }
}
