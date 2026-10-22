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
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;

/**
 * {@code read_file} - reads lines of a workspace file, from the start (head), a given line, or the end (tail).
 */
class ReadFileTool extends AbstractWorkspaceTool {

    static final String NAME = "read_file";
    static final String START_ARG = "startLine";
    static final String COUNT_ARG = "lineCount";
    static final int DEFAULT_LINES = 100;
    /// Room left in a read for a line's number and the note saying it was cut short.
    static final int LINE_NUMBER_ROOM = 100;

    @Inject
    ReadFileTool(final ChatWorkspace workspace) {
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
                        .general("Reads lines of a ")
                        .chatOnly("workspace ")
                        .general("file, numbered from 1, each in full. Give startLine to start part way through, or "
                                 + "a negative startLine to read from the end (-20 reads the last 20 lines). Read "
                                 + "large files a part at a time")
                        .chatOnly(", or use grep to find what you need")
                        .general(".")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The file")
                                .chatOnly(", e.g. /outputs/result.csv")
                                .general(".")
                                .build())
                        .addIntegerProperty(START_ARG, "The first line to read (default 1), or negative to count "
                                                       + "back from the end.")
                        .addIntegerProperty(COUNT_ARG, "How many lines to read (default " + DEFAULT_LINES + ").")
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final long total = workspace.lineCount(path);
        final int start = Objects.requireNonNullElse(intArg(request, START_ARG), 1);
        final int maxLines = limits(context).getMaxLinesPerRead();
        final int count = Math.clamp(Objects.requireNonNullElse(intArg(request, COUNT_ARG), DEFAULT_LINES),
                1, maxLines);
        final long from = start < 0
                ? Math.max(0, total + start)
                : Math.max(0, start - 1L);
        if (from >= total) {
            return ToolResult.text(
                    path + " has " + total + " lines, so there is nothing from line " + (from + 1) + ".");
        }
        final List<String> lines = workspace.readLines(path, from, count);
        // Lines are shown in full, however long, so code can be copied exactly; only a line too long for a whole
        // read is cut short.
        final int maxChars = limits(context).getMaxCharsPerRead();
        final int lineLimit = Math.max(LINE_NUMBER_ROOM, maxChars - LINE_NUMBER_ROOM);
        final StringBuilder sb = new StringBuilder();
        long lineNo = from + 1;
        int shown = 0;
        for (final String line : lines) {
            final String numbered = String.format("%6d\t%s%n", lineNo, shortenLine(line, lineLimit));
            if (shown > 0 && sb.length() + numbered.length() > maxChars) {
                break;
            }
            sb.append(numbered);
            lineNo++;
            shown++;
        }
        final long last = from + shown;
        final String header = path + " (lines " + (from + 1) + "-" + last + " of " + total + "):\n";
        final String footer = last < total
                ? "\n(" + (total - last) + " more lines; read on from line " + (last + 1) + ")"
                : "";
        return ToolResult.text(header + sb.toString().stripTrailing() + footer);
    }
}
