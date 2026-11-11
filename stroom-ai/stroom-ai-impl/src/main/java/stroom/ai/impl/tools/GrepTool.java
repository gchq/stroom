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
                                 + "matched literally unless regex is true; when nothing matches it literally and "
                                 + "it looks like a regular expression (e.g. `a|b`), it is searched for as one, "
                                 + "unless regex is false.")
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
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
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
        try {
            Found found = search(workspace, files, pattern, temp, maxResults, limits(context).getMaxCharsPerRead());
            String note = "";
            final SafePattern asRegex = found.matches() == 0 && rawArg(request, REGEX_ARG) == null
                    ? asRegex(request, pattern)
                    : null;
            if (asRegex != null) {
                // Seen: patterns such as `a|b` sent again and again without regex, each time matching nothing.
                found = search(workspace, files, asRegex, temp, maxResults, limits(context).getMaxCharsPerRead());
                note = "Nothing matched the pattern as literal text, so it was searched for as a regular "
                       + "expression (give regex: false to search for the text alone).\n";
            }
            if (temp != null) {
                workspace.write(out, temp, found.sourceDocUuid());
                return ToolResult.text(note + "Wrote " + found.matches() + " matching line(s) to " + out + "."
                                       + (found.matches() == 0
                        ? literalNote(pattern, asRegex)
                        : ""));
            }
            if (found.matches() == 0) {
                return ToolResult.text(note + "No lines match." + literalNote(pattern, asRegex));
            }
            final int shown = (int) found.shown().lines().count();
            return ToolResult.text(note + found.shown().strip() + (found.matches() > shown
                    ? "\n(" + found.matches() + " matching lines in all; " + (found.matches() - shown) + " not "
                      + "shown. Give out to write them all to a file.)"
                    : ""));
        } finally {
            if (temp != null) {
                ChatWorkspace.deleteQuietly(temp);
            }
        }
    }

    // The pattern as a regular expression, when it was searched for literally but looks like one; null if it
    // doesn't, or isn't a valid one.
    private SafePattern asRegex(final ToolCall request, final SafePattern pattern) {
        if (!pattern.isLiteralThatLooksLikeRegex()) {
            return null;
        }
        try {
            return SafePattern.of(rawArg(request, PATTERN_ARG), true, booleanArg(request, IGNORE_CASE_ARG, false));
        } catch (final RuntimeException e) {
            return null;
        }
    }

    // Each file's matching lines: written to temp if it is given, else as many as are shown.
    private Found search(final SessionWorkspace workspace,
                         final List<WorkspacePath> files,
                         final SafePattern pattern,
                         final Path temp,
                         final int maxResults,
                         final int maxChars) {
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
                            } else if (matches <= maxResults && sb.length() < maxChars) {
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
            return new Found(matches, sb.toString(), sourceDocUuid);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @return What to add when nothing matched, if the text was searched for literally but looks like a regular
    /// expression (and wasn't then searched for as one), so the model doesn't take the miss for an answer.
    private static String literalNote(final SafePattern pattern, final SafePattern asRegex) {
        return pattern.isLiteralThatLooksLikeRegex() && asRegex == null
                ? " The pattern was searched for as literal text, but it looks like a regular expression: to search "
                  + "with it as one, call grep again with regex: true."
                : "";
    }

    // What a search found: how many lines matched, those shown, and the document the first file came from.
    private record Found(int matches, String shown, String sourceDocUuid) {

    }
}
