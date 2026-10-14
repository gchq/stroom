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

package stroom.ai.impl.tools;

import stroom.ai.api.TableSource;
import stroom.ai.api.TableSummariser;
import stroom.ai.api.TableSummaryRequest;
import stroom.ai.api.TableSummaryResult;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Order;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Summarises a free-text column of a table in the workspace. The column is first reduced in code to its distinct
 * patterns (numbers, IPs, dates and IDs masked) with counts, which is usually small enough to give the model as it
 * is. Only if it is still too large does the model summarise the patterns in batches (the automatic fallback), and
 * that summary is saved to the workspace so it is not worked out again.
 */
class ColumnSummariser {

    static final String SUMMARIES_DIR = "/outputs/summaries/";

    private final ChatWorkspace workspace;
    private final TableSummariser tableSummariser;
    private final Provider<AskStroomAiConfig> configProvider;

    @Inject
    ColumnSummariser(final ChatWorkspace workspace,
                     final TableSummariser tableSummariser,
                     final Provider<AskStroomAiConfig> configProvider) {
        this.workspace = workspace;
        this.tableSummariser = tableSummariser;
        this.configProvider = configProvider;
    }

    /**
     * @param chatId The chat.
     * @param path   The table.
     * @param column The column.
     * @param focus  What to look for, or null.
     * @return The column's patterns, or a summary of them, for the model.
     */
    String summarise(final int chatId, final WorkspacePath path, final String column, final String focus) {
        if (column == null || column.isBlank()) {
            throw new WorkspaceException("Give the column to summarise.");
        }
        final WorkspacePath summaryPath = summaryPath(path, column);
        if (focus == null && workspace.find(chatId, summaryPath).isPresent()) {
            return "This column was summarised before (" + summaryPath + "):\n\n"
                   + String.join("\n", workspace.readLines(chatId, summaryPath, 0,
                    workspace.limits().getMaxLinesPerRead()));
        }

        final Result patterns = TableEngine.run(workspace.localFile(chatId, path), Format.forPath(path),
                new Query(List.of(column), List.of(), List.of(), List.of(), true, true,
                        List.of(new Order("count", true)), null));
        final String table = markdown(patterns);
        final String header = "Column '" + column + "' of " + path + ": " + patterns.rowsMatched() + " rows reduce "
                              + "to " + patterns.rows().size() + " distinct patterns (numbers, IPs, dates and IDs "
                              + "masked), most common first.";
        if (table.length() <= workspace.limits().getMaxCharsPerRead()) {
            return header + "\n\n" + table;
        }

        // Too many patterns to give the model, so have it summarise them in batches.
        final ToolRunContext context = ToolRunContext.current()
                .orElseThrow(() -> new WorkspaceException("Summarising needs a model, so can only be done in a "
                                                          + "chat."));
        final Path temp = workspace.tempFile();
        try {
            Files.writeString(temp, table, StandardCharsets.UTF_8);
            final TableSummaryResult result = tableSummariser.summarise(TableSummaryRequest.builder()
                    .source(new TableSource("Patterns of column '" + column + "' of " + path, temp, false))
                    .chatModel(context.getChatModel())
                    .modelRef(NullSafe.get(context.getModelDoc(), OpenAIModelDoc::asDocRef))
                    .config(tableAnalysisConfig())
                    .query(NullSafe.nonBlankStringElse(focus, "Summarise the kinds of value in this column, how "
                                                              + "common each is, and anything unusual. Keep "
                                                              + "counts exact."))
                    .cancelled(context.getCancelled())
                    .build());
            if (!result.summarised()) {
                return header + " They could not be summarised (" + result.text() + "). Query them with the "
                       + "table tool instead.";
            }
            workspace.write(chatId, summaryPath, result.text(), workspace.sourceDocUuid(chatId, path));
            return header + " There are too many to show, so they have been summarised (saved to " + summaryPath
                   + "):\n\n" + result.text();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            ChatWorkspace.deleteQuietly(temp);
        }
    }

    private TableAnalysisConfig tableAnalysisConfig() {
        return Optional.ofNullable(configProvider.get())
                .map(AskStroomAiConfig::getTableAnalysis)
                .orElseGet(TableAnalysisConfig::new);
    }

    static WorkspacePath summaryPath(final WorkspacePath path, final String column) {
        final String file = path.segments().isEmpty()
                ? "table"
                : path.segments().getLast();
        return WorkspacePath.parse(SUMMARIES_DIR + safe(file) + "-" + safe(column) + ".md");
    }

    private static String safe(final String name) {
        final String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.length() > 60
                ? safe.substring(0, 60)
                : safe;
    }

    private static String markdown(final Result result) {
        final StringBuilder sb = new StringBuilder("| ").append(String.join(" | ", result.columns())).append(" |\n");
        sb.append("|").append(" --- |".repeat(result.columns().size())).append('\n');
        for (final List<String> row : result.rows()) {
            sb.append("|");
            for (final String cell : row) {
                sb.append(' ').append(cell.replace("|", "\\|").replace('\n', ' ')).append(" |");
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
