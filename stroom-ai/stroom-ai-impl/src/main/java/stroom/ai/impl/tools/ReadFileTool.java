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
import stroom.ai.impl.workspace.WorkspacePath;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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

    @Inject
    ReadFileTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Reads lines of a workspace file, numbered from 1. Give startLine to start part way "
                             + "through, or a negative startLine to read from the end (-20 reads the last 20 "
                             + "lines). Read large files a part at a time, or use grep to find what you need.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The file, e.g. /outputs/result.csv.")
                        .addIntegerProperty(START_ARG, "The first line to read (default 1), or negative to count "
                                                       + "back from the end.")
                        .addIntegerProperty(COUNT_ARG, "How many lines to read (default " + DEFAULT_LINES + ").")
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final long total = workspace.lineCount(chatId, path);
        final int start = Objects.requireNonNullElse(intArg(request, START_ARG), 1);
        final int maxLines = limits().getMaxLinesPerRead();
        final int count = Math.clamp(Objects.requireNonNullElse(intArg(request, COUNT_ARG), DEFAULT_LINES),
                1, maxLines);
        final long from = start < 0
                ? Math.max(0, total + start)
                : Math.max(0, start - 1L);
        if (from >= total) {
            return path + " has " + total + " lines, so there is nothing from line " + (from + 1) + ".";
        }
        final List<String> lines = workspace.readLines(chatId, path, from, count);
        final StringBuilder sb = new StringBuilder();
        long lineNo = from + 1;
        int shown = 0;
        for (final String line : lines) {
            final String numbered = String.format("%6d\t%s%n", lineNo, shortenLine(line));
            if (sb.length() + numbered.length() > limits().getMaxCharsPerRead()) {
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
        return header + sb.toString().stripTrailing() + footer;
    }
}
