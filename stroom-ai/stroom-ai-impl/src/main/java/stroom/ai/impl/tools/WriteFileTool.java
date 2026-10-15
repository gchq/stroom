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
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Writes a file in /outputs/, replacing any file already at that path.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The file, e.g. /outputs/summary.md.")
                        .addStringProperty(CONTENT_ARG, "The content.")
                        .required(PATH_ARG, CONTENT_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final String content = rawArg(request, CONTENT_ARG);
        if (content == null) {
            throw new WorkspaceException("Give the content to write.");
        }
        workspace.write(chatId, path, content, null);
        return "Wrote " + path + " (" + content.lines().count() + " lines).";
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
