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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;

/**
 * {@code summarise_column} - summarises a free-text column (e.g. log messages) of a table in the workspace. See
 * {@link ColumnSummariser}.
 */
class SummariseColumnTool extends AbstractWorkspaceTool {

    static final String NAME = "summarise_column";
    static final String COLUMN_ARG = "column";
    static final String FOCUS_ARG = "focus";

    private final ColumnSummariser columnSummariser;

    @Inject
    SummariseColumnTool(final ChatWorkspace workspace, final ColumnSummariser columnSummariser) {
        super(workspace);
        this.columnSummariser = columnSummariser;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Summarises a free-text column of a table (e.g. log messages): first reduces it to "
                             + "its distinct patterns with counts (numbers, IPs, dates and IDs masked), and only "
                             + "if there are still too many has them summarised. Use the table tool for exact "
                             + "figures.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The table, e.g. /attachments/12.md.")
                        .addStringProperty(COLUMN_ARG, "The column.")
                        .addStringProperty(FOCUS_ARG, "What to look for, if anything in particular.")
                        .required(PATH_ARG, COLUMN_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        return columnSummariser.summarise(chatId, pathArg(request, PATH_ARG), stringArg(request, COLUMN_ARG),
                stringArg(request, FOCUS_ARG));
    }
}
