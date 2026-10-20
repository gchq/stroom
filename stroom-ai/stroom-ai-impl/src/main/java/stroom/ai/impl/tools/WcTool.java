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

import jakarta.inject.Inject;

/**
 * {@code wc} - says how large a workspace file is, in lines and bytes.
 */
class WcTool extends AbstractWorkspaceTool {

    static final String NAME = "wc";

    @Inject
    WcTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Says how many lines and bytes a ")
                        .chatOnly("workspace ")
                        .general("file has.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The file.")
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        return ToolResult.text(path + ": " + workspace.lineCount(path) + " lines, " + workspace.size(path)
                               + " bytes");
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
