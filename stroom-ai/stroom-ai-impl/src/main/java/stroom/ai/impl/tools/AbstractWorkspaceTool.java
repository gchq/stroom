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
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Base for the tools that work on the files in a session's workspace. Each call runs within the session's tool time
 * limit, can only reach the workspace of the session it is made in (see {@link ToolCallContext#workspace()}), is
 * bounded by the session's limits (see {@link ToolCallContext#limits()}), and returns problems (bad paths, missing
 * files, limits) to the model as an error it can act on.
 */
public abstract class AbstractWorkspaceTool extends AbstractBuiltInTool {

    public static final String PATH_ARG = "path";
    public static final String OUT_ARG = "out";
    /**
     * Lines longer than this are cut short when shown.
     */
    public static final int MAX_LINE_CHARS = 2_000;

    // The workspaces of all sessions, to run a call within its time limit and check where it may write. A call
    // reaches its own session's files, and its limits, through its context.
    private final ChatWorkspace workspaces;

    AbstractWorkspaceTool(final ChatWorkspace workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    public final ToolResult execute(final ToolCall request, final ToolCallContext context) {
        if (context.workspace().isEmpty()) {
            return ToolResult.text(onlyInAChat(definition().name()));
        }
        try {
            final ToolResult result = workspaces.withTimeLimit(timeLimit(request, context),
                    () -> run(request, context));
            return result.withResources(written(context));
        } catch (final WorkspaceException | ToolSessionException e) {
            // ToolSessionException: the tool needs the chat (e.g. the tab the user attached), and this isn't one.
            return ToolResult.text("Error: " + e.getMessage());
        }
    }

    // The files the call wrote, which its text names, as the result's resources.
    private List<ToolResource> written(final ToolCallContext context) {
        final String description = "Written by the " + definition().name() + " tool";
        return sessionWorkspace(context).written().stream()
                .map(path -> ToolResource.workspaceFile(path, description))
                .toList();
    }

    /// @param context The call's context, which [#execute] has checked has a workspace.
    /// @return The workspace of the session the call is made in.
    protected static SessionWorkspace sessionWorkspace(final ToolCallContext context) {
        return context.workspace()
                .orElseThrow(() -> new ToolSessionException("This tool can only be used in a chat."));
    }

    /// @param context The call's context.
    /// @return How long a call of the tool may take in the call's session: by default the session's tool time
    /// limit ([ToolCallContext#timeLimit()]). Tools that run queries override it with the session's query time
    /// limit.
    protected Duration timeLimit(final ToolCallContext context) {
        return context.timeLimit()
                .orElseGet(() -> limits(context).getToolTimeLimit().getDuration());
    }

    /// @param request The model's call.
    /// @param context The call's context.
    /// @return How long this call may take: by default, as long as any call of the tool in the session
    /// ([#timeLimit(ToolCallContext)]).
    protected Duration timeLimit(final ToolCall request, final ToolCallContext context) {
        return timeLimit(context);
    }

    /**
     * Runs the tool.
     *
     * @param request The model's call.
     * @param context The session it is made in, which has a workspace (see {@link #sessionWorkspace}).
     * @return The result for the model, with any files the call wrote and any user actions it offers.
     * @throws WorkspaceException for a problem the model can act on.
     */
    protected abstract ToolResult run(ToolCall request, ToolCallContext context);

    @Override
    public boolean boundsItsOutput() {
        return true;
    }

    /**
     * @return The path argument.
     * @throws WorkspaceException if it is missing or not a valid workspace path.
     */
    protected WorkspacePath pathArg(final ToolCall request, final String name) {
        return WorkspacePath.parse(stringArg(request, name));
    }

    /**
     * @return The value of a true/false argument, or the default if it is absent.
     */
    protected boolean booleanArg(final ToolCall request, final String name, final boolean other) {
        final String value = stringArg(request, name);
        return value == null
                ? other
                : Boolean.parseBoolean(value.toLowerCase(Locale.ROOT));
    }

    /**
     * @return The optional out path argument, or null.
     */
    protected WorkspacePath outArg(final ToolCall request) {
        final String out = stringArg(request, OUT_ARG);
        if (out == null) {
            return null;
        }
        final WorkspacePath path = WorkspacePath.parse(out);
        workspaces.checkWritable(path);
        return path;
    }

    /**
     * @return Result lines for the model, as many as fit within the session's limits, with a note of how many more
     * there are.
     */
    protected static String boundedLines(final ToolCallContext context, final List<String> lines, final long total) {
        final StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (final String line : lines) {
            final String shortened = shortenLine(line);
            if (shown >= limits(context).getMaxLinesPerRead()
                || sb.length() + shortened.length() > limits(context).getMaxCharsPerRead()) {
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

    /// @param context The call's context.
    /// @return The limits of the call's session (see [ToolCallContext#limits()]).
    protected static AiWorkspaceConfig limits(final ToolCallContext context) {
        return context.limits().workspace();
    }

    /**
     * @return A line cut short if it is very long.
     */
    public static String shortenLine(final String line) {
        return shortenLine(line, MAX_LINE_CHARS);
    }

    /**
     * @return A line cut short if it is longer than the given number of characters.
     */
    public static String shortenLine(final String line, final int maxChars) {
        return line.length() > maxChars
                ? line.substring(0, maxChars) + "… (" + (line.length() - maxChars) + " more characters)"
                : line;
    }

    /**
     * @return A size in bytes, for people.
     */
    public static String size(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
