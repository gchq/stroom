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
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Says how many lines and bytes a workspace file has.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The file.")
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        return path + ": " + workspace.lineCount(chatId, path) + " lines, " + workspace.size(chatId, path)
               + " bytes";
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
