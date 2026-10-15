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

import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.memory.MemoryPath;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;

import java.util.List;

/**
 * {@code memory} - lists, reads and changes the agent's memory: the user's own memory, loaded into every chat,
 * and the chat's own memory. See {@link AiMemoryService} for the limits and when a change needs approval.
 */
class MemoryTool extends AbstractBuiltInTool {

    static final String NAME = "memory";
    static final String COMMAND_ARG = "command";
    static final String PATH_ARG = "path";
    static final String CONTENT_ARG = "content";
    static final String OLD_TEXT_ARG = "oldText";
    static final String NEW_TEXT_ARG = "newText";
    static final String VERSION_ARG = "version";

    private final AiMemoryService memoryService;

    @Inject
    MemoryTool(final AiMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Lists, reads and changes your memory. " + MemoryPath.USER_PREFIX + "<name> is the "
                             + "user's memory, loaded into every chat (keep it short; the user sees, and may need "
                             + "to approve, every change). " + MemoryPath.CHAT_PREFIX + "<name> is this chat's "
                             + "memory, e.g. plan.md and notes.md, which survives the chat being compacted. "
                             + "Commands: list; read (path); write (path, content, and the version you read if "
                             + "it exists) to create or replace; append (path, content); replace (path, oldText, "
                             + "newText) where oldText appears exactly once; delete (path, version).")
                .parameters(JsonObjectSchema.builder()
                        .addEnumProperty(COMMAND_ARG,
                                List.of("list", "read", "write", "append", "replace", "delete"),
                                "What to do.")
                        .addStringProperty(PATH_ARG, "The memory's path, e.g. " + MemoryPath.CHAT_PREFIX
                                                     + "notes.md.")
                        .addStringProperty(CONTENT_ARG, "The content to write or append.")
                        .addStringProperty(OLD_TEXT_ARG, "For replace: the text to replace.")
                        .addStringProperty(NEW_TEXT_ARG, "For replace: the text to replace it with.")
                        .addIntegerProperty(VERSION_ARG, "For write (of existing memory) and delete: the version "
                                                         + "you last read.")
                        .required(COMMAND_ARG)
                        .build())
                .build();
    }

    @Override
    public String execute(final ToolExecutionRequest request) {
        return "Error: " + NAME + " can only be used in a chat.";
    }

    @Override
    public String execute(final ToolExecutionRequest request, final int chatId) {
        final String command = stringArg(request, COMMAND_ARG);
        final String path = stringArg(request, PATH_ARG);
        return switch (command == null
                ? ""
                : command) {
            case "list" -> memoryService.list(chatId);
            case "read" -> memoryService.read(chatId, path);
            case "write" -> memoryService.write(chatId, path, rawArg(request, CONTENT_ARG),
                    intArg(request, VERSION_ARG));
            case "append" -> memoryService.append(chatId, path, rawArg(request, CONTENT_ARG));
            case "replace" -> memoryService.replace(chatId, path, rawArg(request, OLD_TEXT_ARG),
                    rawArg(request, NEW_TEXT_ARG));
            case "delete" -> memoryService.delete(chatId, path, intArg(request, VERSION_ARG));
            default -> "Error: the command must be one of list, read, write, append, replace or delete.";
        };
    }

    @Override
    public boolean readsUntrustedContent() {
        // Memory only holds what the user wrote, or changes they could see (and approve) being made.
        return false;
    }

    /// @return False. Some of its commands change memory.
    @Override
    public boolean isReadOnly() {
        return false;
    }
}
