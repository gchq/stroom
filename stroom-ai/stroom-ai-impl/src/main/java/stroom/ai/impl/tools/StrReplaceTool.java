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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * {@code str_replace} - replaces some text in a file in {@code /outputs}. The text must appear exactly once, so
 * the change is never made in the wrong place.
 */
class StrReplaceTool extends AbstractWorkspaceTool {

    static final String NAME = "str_replace";
    static final String OLD_TEXT_ARG = "oldText";
    static final String NEW_TEXT_ARG = "newText";

    @Inject
    StrReplaceTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Replaces some text in a file in /outputs/. The text must appear exactly once.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The file.")
                        .addStringProperty(OLD_TEXT_ARG, "The text to replace, exactly as it is in the file.")
                        .addStringProperty(NEW_TEXT_ARG, "The text to replace it with.")
                        .required(PATH_ARG, OLD_TEXT_ARG, NEW_TEXT_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final String oldText = rawArg(request, OLD_TEXT_ARG);
        final String newText = rawArg(request, NEW_TEXT_ARG);
        if (oldText == null || oldText.isEmpty() || newText == null) {
            throw new WorkspaceException("Give the text to replace and the text to replace it with.");
        }
        // Checked before reading, so a read-only file gives the right error.
        workspace.checkWritable(path);
        final String content;
        try {
            content = Files.readString(workspace.localFile(chatId, path), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final int index = content.indexOf(oldText);
        if (index < 0) {
            throw new WorkspaceException("The text to replace is not in " + path + ". Read the file again.");
        }
        if (content.indexOf(oldText, index + 1) >= 0) {
            throw new WorkspaceException("The text to replace appears more than once in " + path
                                         + ". Give more of it, so it appears once.");
        }
        workspace.write(chatId, path,
                content.substring(0, index) + newText + content.substring(index + oldText.length()),
                workspace.sourceDocUuid(chatId, path));
        return "Replaced the text in " + path + ".";
    }

    @Override
    public boolean readsUntrustedContent() {
        return false;
    }
}
