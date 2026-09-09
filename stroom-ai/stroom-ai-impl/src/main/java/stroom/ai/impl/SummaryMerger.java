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
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Merges the summaries produced by batch analysis into a single answer.
 * <p>
 * The summaries are merged a chunk at a time rather than all in one call, because the one call that
 * has to hold every summary is the one most likely to exceed the context window - and that is the
 * call whose failure would otherwise throw away every batch result behind it. A merge is only ever an
 * improvement on its input, so where one fails its inputs are carried forward as they are. The caller
 * always gets everything that was produced, condensed as far as the model managed.
 * </p>
 */
class SummaryMerger {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SummaryMerger.class);

    /**
     * How many summaries are merged in one call. Merging in chunks keeps the merge input bounded no
     * matter how many batches the data was split into.
     */
    private static final int MAX_SUMMARIES_PER_MERGE = 10;

    private final ChatModel chatModel;
    private final TableAnalysisConfig config;
    private final StringBuilder debugLog;
    private final DebugFormatter debugFormatter;

    /**
     * @param debugLog       Where to record each call, or null if the chat is not keeping debug detail.
     * @param debugFormatter Renders a call and its response for that record.
     */
    SummaryMerger(final ChatModel chatModel,
                  final TableAnalysisConfig config,
                  final StringBuilder debugLog,
                  final DebugFormatter debugFormatter) {
        this.chatModel = chatModel;
        this.config = config;
        this.debugLog = debugLog;
        this.debugFormatter = debugFormatter;
    }

    /**
     * @param notes Told when the summaries could not be condensed, so the reader knows why the answer
     *              reads as a list rather than as an answer.
     * @return The summaries condensed as far as the model managed, never less than what went in.
     */
    String merge(final List<String> summaries, final AnswerNotes notes) {
        LOGGER.debug(() -> "merge: merging " + summaries.size() + " summaries");

        List<String> current = summaries;
        boolean degraded = false;

        while (current.size() > 1) {
            final List<String> merged = new ArrayList<>();
            for (int i = 0; i < current.size(); i += MAX_SUMMARIES_PER_MERGE) {
                final List<String> chunk = current.subList(
                        i, Math.min(i + MAX_SUMMARIES_PER_MERGE, current.size()));
                if (chunk.size() == 1) {
                    merged.add(chunk.getFirst());
                } else {
                    try {
                        merged.add(mergeChunk(chunk));
                    } catch (final RuntimeException e) {
                        if (e.getCause() instanceof CancellationException) {
                            LOGGER.warn(() -> "Chat summary merge operation aborted", e);
                        } else {
                            // Keep the summaries rather than lose them. They are still an answer, just a
                            // longer and more repetitive one than the merged version would have been.
                            LOGGER.warn(() -> "Failed to merge " + chunk.size()
                                              + " summaries, keeping them unmerged", e);
                        }
                        degraded = true;
                        merged.addAll(chunk);
                    }
                }
            }

            if (merged.size() >= current.size()) {
                // No progress, so another round would not make any either.
                current = merged;
                break;
            }
            current = merged;
        }

        if (degraded) {
            notes.add("These results could not be condensed into a single summary, so they are shown "
                      + "as they were produced");
        }

        return current.size() == 1
                ? current.getFirst()
                : join(current);
    }

    /**
     * Lays summaries out as the numbered list the merge prompt expects.
     */
    static String join(final List<String> summaries) {
        final StringBuilder combined = new StringBuilder();
        for (int i = 0; i < summaries.size(); i++) {
            combined
                    .append("--- Summary ")
                    .append(i + 1)
                    .append(" ---\n")
                    .append(summaries.get(i))
                    .append("\n\n");
        }
        return combined.toString();
    }

    /**
     * Merges one chunk of summaries with a single LLM call.
     */
    private String mergeChunk(final List<String> summaries) {
        final String mergePromptTemplate = config.getMultiSummaryMergePrompt() != null
                ? config.getMultiSummaryMergePrompt()
                : TableAnalysisConfig.DEFAULT_MULTI_SUMMARY_MERGE_PROMPT;
        final String mergePrompt = mergePromptTemplate
                .replace("{{summaries}}", join(summaries));

        LOGGER.trace(() -> "mergeChunk prompt:\n" + mergePrompt);

        final List<ChatMessage> messages = List.of(
                new SystemMessage("You merge partial answers into a unified, concise summary."),
                new UserMessage(mergePrompt));

        final ChatResponse response = LOGGER.logDurationIfDebugEnabled(
                () -> chatModel.chat(messages),
                r -> "mergeChunk: summaries=" + summaries.size()
                     + " responseLength=" + r.aiMessage().text().length());
        LOGGER.trace(() -> "mergeChunk response:\n" + response.aiMessage().text());

        final String responseText = response.aiMessage().text();

        if (debugLog != null) {
            synchronized (debugLog) {
                debugLog
                        .append("#### Merge Step (")
                        .append(summaries.size())
                        .append(" summaries)\n\n")
                        .append(debugFormatter.format(messages, responseText));
            }
        }

        return responseText;
    }


    // --------------------------------------------------------------------------------


    /**
     * Renders a call and its response for the debug detail a chat can be asked to keep.
     */
    interface DebugFormatter {

        String format(List<ChatMessage> messages, String response);
    }
}
