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

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Base for the tools that work on the files in a chat's workspace. Each call runs within the tool time limit, can
 * only reach the workspace of the chat it is made in, and returns problems (bad paths, missing files, limits) to
 * the model as an error it can act on.
 */
abstract class AbstractWorkspaceTool extends AbstractBuiltInTool {

    static final String PATH_ARG = "path";
    static final String OUT_ARG = "out";
    /**
     * Lines longer than this are cut short when shown.
     */
    static final int MAX_LINE_CHARS = 2_000;

    protected final ChatWorkspace workspace;

    AbstractWorkspaceTool(final ChatWorkspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public final String execute(final ToolExecutionRequest request) {
        return "Error: " + specification().name() + " can only be used in a chat.";
    }

    @Override
    public final String execute(final ToolExecutionRequest request, final int chatId) {
        try {
            return workspace.withTimeLimit(timeLimit(request), () -> run(request, chatId));
        } catch (final WorkspaceException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * @return How long a call may take.
     */
    protected Duration timeLimit() {
        return limits().getToolTimeLimit().getDuration();
    }

    /// @param request The model's call.
    /// @return How long this call may take: by default, as long as any call of the tool ([#timeLimit()]).
    protected Duration timeLimit(final ToolExecutionRequest request) {
        return timeLimit();
    }

    /**
     * Runs the tool.
     *
     * @param request The model's call.
     * @param chatId  The chat it is made in.
     * @return The result for the model.
     * @throws WorkspaceException for a problem the model can act on.
     */
    abstract String run(ToolExecutionRequest request, int chatId);

    @Override
    public boolean boundsItsOutput() {
        return true;
    }

    /**
     * @return The path argument.
     * @throws WorkspaceException if it is missing or not a valid workspace path.
     */
    protected WorkspacePath pathArg(final ToolExecutionRequest request, final String name) {
        return WorkspacePath.parse(stringArg(request, name));
    }

    /**
     * @return The value of a true/false argument, or the default if it is absent.
     */
    protected boolean booleanArg(final ToolExecutionRequest request, final String name, final boolean other) {
        final String value = stringArg(request, name);
        return value == null
                ? other
                : Boolean.parseBoolean(value.toLowerCase(Locale.ROOT));
    }

    /**
     * @return The optional out path argument, or null.
     */
    protected WorkspacePath outArg(final ToolExecutionRequest request) {
        final String out = stringArg(request, OUT_ARG);
        if (out == null) {
            return null;
        }
        final WorkspacePath path = WorkspacePath.parse(out);
        workspace.checkWritable(path);
        return path;
    }

    /**
     * @return Result lines for the model, as many as fit, with a note of how many more there are.
     */
    protected String boundedLines(final List<String> lines, final long total) {
        final StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (final String line : lines) {
            final String shortened = shortenLine(line);
            if (shown >= limits().getMaxLinesPerRead()
                || sb.length() + shortened.length() > limits().getMaxCharsPerRead()) {
                break;
            }
            sb.append(shortened).append('\n');
            shown++;
        }
        if (total == 0) {
            return "No results.";
        }
        return sb.toString().stripTrailing() + (total > shown
                ? "\n(" + total + " results in all; " + (total - shown) + " not shown. Give out to write them "
                  + "all to a file.)"
                : "");
    }

    protected AiWorkspaceConfig limits() {
        return workspace.limits();
    }

    /**
     * @return A line cut short if it is very long.
     */
    static String shortenLine(final String line) {
        return line.length() > MAX_LINE_CHARS
                ? line.substring(0, MAX_LINE_CHARS) + "… (" + (line.length() - MAX_LINE_CHARS) + " more characters)"
                : line;
    }

    /**
     * @return A size in bytes, for people.
     */
    static String size(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
