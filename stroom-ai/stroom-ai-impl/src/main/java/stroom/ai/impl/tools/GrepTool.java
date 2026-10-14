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

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.ChatWorkspace.Entry;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspacePath;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * {@code grep} - finds the lines of workspace files that contain some text (literally, unless a regular
 * expression is asked for). The matching lines can be written to a file instead of being returned, to keep them
 * out of the conversation.
 */
class GrepTool extends AbstractWorkspaceTool {

    static final String NAME = "grep";
    static final String PATTERN_ARG = "pattern";
    static final String REGEX_ARG = "regex";
    static final String IGNORE_CASE_ARG = "ignoreCase";
    static final String MAX_RESULTS_ARG = "maxResults";
    static final String OUT_ARG = "out";
    static final int DEFAULT_MAX_RESULTS = 50;
    static final int MAX_FILES = 100;
    private static final int BATCH_LINES = 5_000;

    @Inject
    GrepTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Finds the lines of a workspace file, or of every file in a directory, that contain "
                             + "some text. The text is matched literally unless regex is true. Give out to write "
                             + "every matching line to a file instead of returning them.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "A file or directory, e.g. /outputs/.")
                        .addStringProperty(PATTERN_ARG, "The text to find.")
                        .addBooleanProperty(REGEX_ARG, "True if the pattern is a regular expression.")
                        .addBooleanProperty(IGNORE_CASE_ARG, "True to ignore case.")
                        .addIntegerProperty(MAX_RESULTS_ARG, "The most matching lines to return (default "
                                                             + DEFAULT_MAX_RESULTS + ").")
                        .addStringProperty(OUT_ARG, "A file in /outputs/ to write the matching lines to.")
                        .required(PATH_ARG, PATTERN_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final SafePattern pattern = SafePattern.of(rawArg(request, PATTERN_ARG),
                booleanArg(request, REGEX_ARG, false),
                booleanArg(request, IGNORE_CASE_ARG, false));
        final int maxResults = Math.clamp(Objects.requireNonNullElse(intArg(request, MAX_RESULTS_ARG),
                DEFAULT_MAX_RESULTS), 1, limits().getMaxLinesPerRead());
        final String outArg = stringArg(request, OUT_ARG);
        final WorkspacePath out = outArg != null
                ? WorkspacePath.parse(outArg)
                : null;

        final List<WorkspacePath> files = path.isFile()
                ? List.of(path)
                : workspace.list(chatId, path).stream()
                        .filter(entry -> entry.unavailable() == null)
                        .map(Entry::path)
                        .toList();
        if (files.size() > MAX_FILES) {
            return "Error: " + path + " holds " + files.size() + " files; search at most " + MAX_FILES
                   + " at once, e.g. a smaller directory.";
        }

        final Path temp = out != null
                ? workspace.tempFile()
                : null;
        try (final BufferedWriter writer = temp != null
                ? Files.newBufferedWriter(temp, StandardCharsets.UTF_8)
                : null) {
            final StringBuilder sb = new StringBuilder();
            int matches = 0;
            String sourceDocUuid = null;
            for (final WorkspacePath file : files) {
                if (sourceDocUuid == null) {
                    sourceDocUuid = workspace.sourceDocUuid(chatId, file);
                }
                final long total = workspace.lineCount(chatId, file);
                for (long from = 0; from < total; from += BATCH_LINES) {
                    long lineNo = from + 1;
                    for (final String line : workspace.readLines(chatId, file, from, BATCH_LINES)) {
                        ToolDeadline.check();
                        if (pattern.find(line)) {
                            matches++;
                            if (writer != null) {
                                writer.write(line);
                                writer.newLine();
                            } else if (matches <= maxResults
                                       && sb.length() < limits().getMaxCharsPerRead()) {
                                sb.append(files.size() > 1
                                        ? file + ":"
                                        : "").append(lineNo).append(": ").append(shortenLine(line)).append('\n');
                            }
                        }
                        lineNo++;
                    }
                }
            }
            if (writer != null) {
                writer.close();
                workspace.write(chatId, out, temp, sourceDocUuid);
                return "Wrote " + matches + " matching line(s) to " + out + ".";
            }
            if (matches == 0) {
                return "No lines match.";
            }
            final int shown = (int) sb.toString().lines().count();
            return sb.toString().strip() + (matches > shown
                    ? "\n(" + matches + " matching lines in all; " + (matches - shown) + " not shown. Give out "
                      + "to write them all to a file.)"
                    : "");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (temp != null) {
                ChatWorkspace.deleteQuietly(temp);
            }
        }
    }
}
