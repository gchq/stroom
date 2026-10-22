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
import stroom.ai.impl.workspace.ChatWorkspace.Entry;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;

import jakarta.inject.Inject;

import java.util.List;

/**
 * {@code ls} - lists the files in the chat's workspace.
 */
class LsTool extends AbstractWorkspaceTool {

    static final String NAME = "ls";
    static final int MAX_ENTRIES = 200;

    @Inject
    LsTool(final ChatWorkspace workspace) {
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
                        .general("Lists the files")
                        .chatOnly(" in this chat's workspace: /attachments/ (tables the user attached, each with a "
                                  + ".profile.md summary; read-only) and /outputs/ (large tool results, and files "
                                  + "you write)")
                        .general(".")
                        .chatOnly(" Read files with read_file, search them with grep.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The directory to list")
                                .chatOnly(", e.g. /outputs/")
                                .general(". Leave out for all.")
                                .build())
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String raw = stringArg(request, PATH_ARG);
        final WorkspacePath dir = raw == null
                ? WorkspacePath.ROOT
                : WorkspacePath.parse(raw);
        final List<Entry> entries = workspace.list(dir);
        if (entries.isEmpty()) {
            return ToolResult.text("There are no files in " + dir + ".");
        }
        final StringBuilder sb = new StringBuilder();
        for (final Entry entry : entries.subList(0, Math.min(entries.size(), MAX_ENTRIES))) {
            sb.append(entry.path()).append("  ");
            if (entry.unavailable() != null) {
                sb.append("(no longer available: ").append(entry.unavailable()).append(')');
            } else {
                sb.append(size(entry.sizeBytes()));
            }
            sb.append('\n');
        }
        if (entries.size() > MAX_ENTRIES) {
            sb.append("(").append(entries.size() - MAX_ENTRIES).append(" more; list a directory to see them)");
        }
        return ToolResult.text(sb.toString().strip());
    }
}
