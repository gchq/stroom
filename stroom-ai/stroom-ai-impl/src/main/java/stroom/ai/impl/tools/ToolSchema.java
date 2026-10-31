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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The JSON Schema of a tool's input, or of part of it, as Stroom's tools describe it. It is Stroom's own, so a
/// tool can be offered to any model or client: [LangChain4jTools] turns it into langchain4j's schema for the chat.
/// The builders mirror langchain4j's (e.g. `ObjectSchema.builder().addStringProperty(name, description)`), and
/// also take a [ToolDescription] where a description has chat-only parts.
public sealed interface ToolSchema permits ToolSchema.ObjectSchema,
        ToolSchema.ArraySchema,
        ToolSchema.StringSchema,
        ToolSchema.IntegerSchema,
        ToolSchema.BooleanSchema,
        ToolSchema.EnumSchema {

    /// @return What the value is, in general and chat-only parts, or null.
    ToolDescription about();

    /// @return What the value is, as the chat's model is shown it (every part of [#about()]), or null.
    default String description() {
        return ToolDescription.forChat(about());
    }

    /// @return The schema with every chat-only part of its descriptions left out, and any property whose
    /// description is wholly chat-only left out with it (see [ToolDefinition#general()]).
    ToolSchema withoutChatOnlyParts();

    private static ToolDescription general(final ToolDescription about) {
        return about == null
                ? null
                : about.withoutChatOnlyParts();
    }

    /// An object, with named properties.
    ///
    /// @param about      What the object is, or null.
    /// @param properties Its properties, by name, in the order they were added.
    /// @param required   The properties that must be given, or null if none were named.
    record ObjectSchema(ToolDescription about, Map<String, ToolSchema> properties, List<String> required)
            implements ToolSchema {

        /// @param about      What the object is, or null.
        /// @param properties Its properties, by name, in the order they were added.
        /// @param required   The properties that must be given, or null if none were named.
        public ObjectSchema {
            properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
            required = required == null
                    ? null
                    : List.copyOf(required);
        }

        /// @return A builder for an object's schema.
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public ObjectSchema withoutChatOnlyParts() {
            final Map<String, ToolSchema> generalProperties = new LinkedHashMap<>();
            properties.forEach((name, property) -> {
                if (property.about() == null || !property.about().isChatOnly()) {
                    generalProperties.put(name, property.withoutChatOnlyParts());
                }
            });
            return new ObjectSchema(general(about),
                    generalProperties,
                    required == null
                            ? null
                            : required.stream().filter(generalProperties::containsKey).toList());
        }

        /// Builds an [ObjectSchema].
        public static final class Builder {

            private final Map<String, ToolSchema> properties = new LinkedHashMap<>();
            private ToolDescription about;
            private List<String> required;

            private Builder() {
            }

            /// @param description What the object is.
            /// @return This builder.
            public Builder description(final String description) {
                return description(ToolDescription.of(description));
            }

            /// @param description What the object is, in general and chat-only parts.
            /// @return This builder.
            public Builder description(final ToolDescription description) {
                this.about = description;
                return this;
            }

            /// @param name   The property's name.
            /// @param schema Its schema.
            /// @return This builder.
            public Builder addProperty(final String name, final ToolSchema schema) {
                properties.put(name, schema);
                return this;
            }

            /// @param name        The property's name.
            /// @param description What it is.
            /// @return This builder.
            public Builder addStringProperty(final String name, final String description) {
                return addStringProperty(name, ToolDescription.of(description));
            }

            /// @param name        The property's name.
            /// @param description What it is, in general and chat-only parts.
            /// @return This builder.
            public Builder addStringProperty(final String name, final ToolDescription description) {
                return addProperty(name, new StringSchema(description));
            }

            /// @param name        The property's name.
            /// @param description What it is.
            /// @return This builder.
            public Builder addIntegerProperty(final String name, final String description) {
                return addProperty(name, new IntegerSchema(ToolDescription.of(description)));
            }

            /// @param name        The property's name.
            /// @param description What it is.
            /// @return This builder.
            public Builder addBooleanProperty(final String name, final String description) {
                return addProperty(name, new BooleanSchema(ToolDescription.of(description)));
            }

            /// @param name        The property's name.
            /// @param values      The values it may have.
            /// @param description What it is.
            /// @return This builder.
            public Builder addEnumProperty(final String name, final List<String> values, final String description) {
                return addProperty(name, new EnumSchema(ToolDescription.of(description), values));
            }

            /// @param required The properties that must be given, replacing any named before.
            /// @return This builder.
            public Builder required(final String... required) {
                this.required = Arrays.asList(required);
                return this;
            }

            /// @param required The properties that must be given, replacing any named before.
            /// @return This builder.
            public Builder required(final List<String> required) {
                this.required = required;
                return this;
            }

            /// @return The schema.
            public ObjectSchema build() {
                return new ObjectSchema(about, properties, required);
            }
        }
    }

    /// A list of values.
    ///
    /// @param about What the list is, or null.
    /// @param items The schema of each item, or null.
    record ArraySchema(ToolDescription about, ToolSchema items) implements ToolSchema {

        /// @return A builder for a list's schema.
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public ArraySchema withoutChatOnlyParts() {
            return new ArraySchema(general(about),
                    items == null
                            ? null
                            : items.withoutChatOnlyParts());
        }

        /// Builds an [ArraySchema].
        public static final class Builder {

            private ToolDescription about;
            private ToolSchema items;

            private Builder() {
            }

            /// @param description What the list is.
            /// @return This builder.
            public Builder description(final String description) {
                return description(ToolDescription.of(description));
            }

            /// @param description What the list is, in general and chat-only parts.
            /// @return This builder.
            public Builder description(final ToolDescription description) {
                this.about = description;
                return this;
            }

            /// @param items The schema of each item.
            /// @return This builder.
            public Builder items(final ToolSchema items) {
                this.items = items;
                return this;
            }

            /// @return The schema.
            public ArraySchema build() {
                return new ArraySchema(about, items);
            }
        }
    }

    /// A string.
    ///
    /// @param about What it is, or null.
    record StringSchema(ToolDescription about) implements ToolSchema {

        /// @return A builder for a string's schema.
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public StringSchema withoutChatOnlyParts() {
            return new StringSchema(general(about));
        }

        /// Builds a [StringSchema].
        public static final class Builder {

            private ToolDescription about;

            private Builder() {
            }

            /// @param description What the string is.
            /// @return This builder.
            public Builder description(final String description) {
                this.about = ToolDescription.of(description);
                return this;
            }

            /// @return The schema.
            public StringSchema build() {
                return new StringSchema(about);
            }
        }
    }

    /// A whole number.
    ///
    /// @param about What it is, or null.
    record IntegerSchema(ToolDescription about) implements ToolSchema {

        @Override
        public IntegerSchema withoutChatOnlyParts() {
            return new IntegerSchema(general(about));
        }
    }

    /// True or false.
    ///
    /// @param about What it is, or null.
    record BooleanSchema(ToolDescription about) implements ToolSchema {

        @Override
        public BooleanSchema withoutChatOnlyParts() {
            return new BooleanSchema(general(about));
        }
    }

    /// One of a list of strings.
    ///
    /// @param about  What it is, or null.
    /// @param values The values it may have.
    record EnumSchema(ToolDescription about, List<String> values) implements ToolSchema {

        /// @param about  What it is, or null.
        /// @param values The values it may have.
        public EnumSchema {
            values = List.copyOf(values);
        }

        @Override
        public EnumSchema withoutChatOnlyParts() {
            return new EnumSchema(general(about), values);
        }
    }
}
