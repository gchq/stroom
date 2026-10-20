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

/// What a [StroomTool] is, as it is shown to a model or client: its name, what it does, and the JSON Schema of its
/// input. It is Stroom's own type: [LangChain4jTools] turns it into langchain4j's `ToolSpecification`.
///
/// Its descriptions (the tool's and its parameters') are made of general and chat-only parts (see
/// [ToolDescription]). The chat shows every part ([#description()]); [#general()] gives the definition with the
/// chat-only parts left out, for a client outside the chat.
///
/// @param name        The tool's name, unique among the tools offered.
/// @param about       What the tool does and when to use it, for the model, or null.
/// @param parameters  The schema of its input, or null if it takes none.
public record ToolDefinition(String name, ToolDescription about, ObjectSchema parameters) {

    /// @return A builder for a definition.
    public static Builder builder() {
        return new Builder();
    }

    /// @return What the tool does, as the chat's model is shown it: every part of [#about()], or null if it has no
    /// description.
    public String description() {
        return ToolDescription.forChat(about);
    }

    /// The definition for a client outside the chat (e.g. over MCP), which has none of the chat's other tools,
    /// workspace or tabs. Nothing uses it yet.
    ///
    /// @return The definition with every chat-only part of its descriptions left out, and any parameter whose
    /// description is wholly chat-only (e.g. an `out` file in the chat's workspace) left out with it.
    public ToolDefinition general() {
        return new ToolDefinition(name,
                about == null
                        ? null
                        : about.withoutChatOnlyParts(),
                parameters == null
                        ? null
                        : parameters.withoutChatOnlyParts());
    }

    /// Builds a [ToolDefinition], as langchain4j's `ToolSpecification.builder()` does.
    public static final class Builder {

        private String name;
        private ToolDescription about;
        private ObjectSchema parameters;

        private Builder() {
        }

        /// @param name The tool's name.
        /// @return This builder.
        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        /// @param description What the tool does, general throughout.
        /// @return This builder.
        public Builder description(final String description) {
            this.about = ToolDescription.of(description);
            return this;
        }

        /// @param description What the tool does, in general and chat-only parts.
        /// @return This builder.
        public Builder description(final ToolDescription description) {
            this.about = description;
            return this;
        }

        /// @param parameters The schema of its input.
        /// @return This builder.
        public Builder parameters(final ObjectSchema parameters) {
            this.parameters = parameters;
            return this;
        }

        /// @return The definition.
        public ToolDefinition build() {
            return new ToolDefinition(name, about, parameters);
        }
    }
}
