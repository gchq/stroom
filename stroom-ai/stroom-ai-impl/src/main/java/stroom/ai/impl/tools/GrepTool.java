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

import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.ChatWorkspace.Entry;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;

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
    static final int DEFAULT_MAX_RESULTS = 50;
    static final int MAX_FILES = 100;
    private static final int BATCH_LINES = 5_000;

    @Inject
    GrepTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Finds the lines of a ")
                        .chatOnly("workspace ")
                        .general("file, or of every file in a directory, that contain some text. The text is "
                                 + "matched literally unless regex is true.")
                        .chatOnly(" Give out to write every matching line to a file instead of returning them.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("A file or directory")
                                .chatOnly(", e.g. /outputs/")
                                .general(".")
                                .build())
                        .addStringProperty(PATTERN_ARG, "The text to find.")
                        .addBooleanProperty(REGEX_ARG, "True if the pattern is a regular expression.")
                        .addBooleanProperty(IGNORE_CASE_ARG, "True to ignore case.")
                        .addIntegerProperty(MAX_RESULTS_ARG, "The most matching lines to return (default "
                                                             + DEFAULT_MAX_RESULTS + ").")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write the matching lines to.")
                                .build())
                        .required(PATH_ARG, PATTERN_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final SafePattern pattern = SafePattern.of(rawArg(request, PATTERN_ARG),
                booleanArg(request, REGEX_ARG, false),
                booleanArg(request, IGNORE_CASE_ARG, false));
        final int maxResults = Math.clamp(Objects.requireNonNullElse(intArg(request, MAX_RESULTS_ARG),
                DEFAULT_MAX_RESULTS), 1, limits(context).getMaxLinesPerRead());
        final String outArg = stringArg(request, OUT_ARG);
        final WorkspacePath out = outArg != null
                ? WorkspacePath.parse(outArg)
                : null;

        final List<WorkspacePath> files = path.isFile()
                ? List.of(path)
                : workspace.list(path).stream()
                        .filter(entry -> entry.unavailable() == null)
                        .map(Entry::path)
                        .toList();
        if (files.size() > MAX_FILES) {
            return ToolResult.text("Error: " + path + " holds " + files.size() + " files; search at most " + MAX_FILES
                                   + " at once, e.g. a smaller directory.");
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
                    sourceDocUuid = workspace.sourceDocUuid(file);
                }
                final long total = workspace.lineCount(file);
                for (long from = 0; from < total; from += BATCH_LINES) {
                    long lineNo = from + 1;
                    for (final String line : workspace.readLines(file, from, BATCH_LINES)) {
                        ToolDeadline.check();
                        if (pattern.find(line)) {
                            matches++;
                            if (writer != null) {
                                writer.write(line);
                                writer.newLine();
                            } else if (matches <= maxResults
                                       && sb.length() < limits(context).getMaxCharsPerRead()) {
                                sb.append(files.size() > 1
                                        ? file + ":"
                                        : "").append(lineNo).append(": ").append(shortenLine(line))
                                        .append(line.length() > MAX_LINE_CHARS
                                                ? " (read_file shows the whole line)"
                                                : "").append('\n');
                            }
                        }
                        lineNo++;
                    }
                }
            }
            if (writer != null) {
                writer.close();
                workspace.write(out, temp, sourceDocUuid);
                return ToolResult.text("Wrote " + matches + " matching line(s) to " + out + "."
                                       + (matches == 0
                        ? literalNote(pattern)
                        : ""));
            }
            if (matches == 0) {
                return ToolResult.text("No lines match." + literalNote(pattern));
            }
            final int shown = (int) sb.toString().lines().count();
            return ToolResult.text(sb.toString().strip() + (matches > shown
                    ? "\n(" + matches + " matching lines in all; " + (matches - shown) + " not shown. Give out "
                      + "to write them all to a file.)"
                    : ""));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (temp != null) {
                ChatWorkspace.deleteQuietly(temp);
            }
        }
    }

    /// @return What to add when nothing matched, if the text was searched for literally but looks like a regular
    /// expression, so the model doesn't take the miss for an answer.
    private static String literalNote(final SafePattern pattern) {
        return pattern.isLiteralThatLooksLikeRegex()
                ? " The pattern was searched for as literal text, but it looks like a regular expression: to search "
                  + "with it as one, call grep again with regex: true."
                : "";
    }
}
