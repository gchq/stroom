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

package stroom.ai.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/// What a tool is for, as tool policies grant it: each of Stroom's own tools has one, for the "Ask Stroom AI" chat
/// and clients over MCP alike. Whether a tool only reads or also changes things is decided per tool, so each
/// category holds both kinds.
public enum AiToolCategory implements HasPrimitiveValue, HasDisplayValue {
    /// Documents, folders and their instructions, and the chat's own tools (questions, the task list, memory and
    /// the workspace files).
    CORE(0, "Core", "Documents, folders and their instructions, the date and time, and working tools: questions, "
                    + "the task list, memory and workspace files."),
    /// StroomQL, events and data sources.
    SEARCH(1, "Search", "StroomQL queries, events and data sources."),
    /// Creating and changing Stroom documents.
    CONTENT(2, "Content", "Creating and changing Stroom documents: translations, pipelines, feeds, queries, "
                          + "dashboards and documentation."),
    /// Indexes and their fields.
    INDEXING(3, "Indexing", "Indexes, their fields, and Elasticsearch index templates."),
    /// Streams, and the processing of them.
    PROCESSING(4, "Processing", "Streams, processor filters and tasks, sampling and stepping."),
    /// Stroom's configuration and nodes.
    ADMINISTRATION(5, "Administration", "Configuration properties and server tasks.");

    public static final PrimitiveValueConverter<AiToolCategory> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiToolCategory.class, AiToolCategory.values());

    private final byte primitiveValue;
    private final String displayValue;
    private final String description;

    AiToolCategory(final int primitiveValue, final String displayValue, final String description) {
        this.primitiveValue = (byte) primitiveValue;
        this.displayValue = displayValue;
        this.description = description;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /// @return What the category's tools are for, as an administrator choosing categories for a policy sees it.
    public String getDescription() {
        return description;
    }
}
