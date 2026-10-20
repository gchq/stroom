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
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import jakarta.inject.Inject;

/**
 * {@code write_file} - writes a file in the chat's workspace, replacing any already there. Only {@code /outputs}
 * can be written. It only changes the chat's own workspace, so it may be used while the user is away.
 */
class WriteFileTool extends AbstractWorkspaceTool {

    static final String NAME = "write_file";
    static final String CONTENT_ARG = "content";

    @Inject
    WriteFileTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Writes a file")
                        .chatOnly(" in /outputs/")
                        .general(", replacing any file already at that path.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The file")
                                .chatOnly(", e.g. /outputs/summary.md")
                                .general(".")
                                .build())
                        .addStringProperty(CONTENT_ARG, "The content.")
                        .required(PATH_ARG, CONTENT_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final String content = rawArg(request, CONTENT_ARG);
        if (content == null) {
            throw new WorkspaceException("Give the content to write.");
        }
        workspace.write(path, content, null);
        return ToolResult.text("Wrote " + path + " (" + content.lines().count() + " lines).");
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }

    /// @return False. It creates or replaces files in the workspace.
    @Override
    public boolean isReadOnly() {
        return false;
    }
}
