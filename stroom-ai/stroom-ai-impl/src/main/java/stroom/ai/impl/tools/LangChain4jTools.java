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

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.BooleanSchema;
import stroom.ai.impl.tools.ToolSchema.EnumSchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Objects;
import java.util.function.Function;

/// The one place Stroom's tool contract ([StroomTool], [ToolDefinition], [ToolSchema], [ToolCall]) meets
/// langchain4j's, for the chat's model: definitions become `ToolSpecification`s, exactly as the tools built them
/// before, and the model's `ToolExecutionRequest`s become [ToolCall]s.
public final class LangChain4jTools {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LangChain4jTools.class);

    private LangChain4jTools() {
    }

    /// @param definition A tool's definition.
    /// @return It as langchain4j's specification, as the model is shown it.
    public static ToolSpecification specification(final ToolDefinition definition) {
        final ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(definition.name())
                .description(definition.description());
        if (definition.parameters() != null) {
            builder.parameters(objectSchema(definition.parameters()));
        }
        return builder.build();
    }

    /// @param schema A schema.
    /// @return It as langchain4j's schema element.
    public static JsonSchemaElement schemaElement(final ToolSchema schema) {
        return switch (schema) {
            case final ObjectSchema object -> objectSchema(object);
            case final ArraySchema array -> {
                final JsonArraySchema.Builder builder = JsonArraySchema.builder()
                        .description(array.description());
                if (array.items() != null) {
                    builder.items(schemaElement(array.items()));
                }
                yield builder.build();
            }
            case final StringSchema string -> JsonStringSchema.builder().description(string.description()).build();
            case final IntegerSchema integer -> JsonIntegerSchema.builder()
                    .description(integer.description())
                    .build();
            case final BooleanSchema bool -> JsonBooleanSchema.builder().description(bool.description()).build();
            case final EnumSchema enumSchema -> JsonEnumSchema.builder()
                    .enumValues(enumSchema.values())
                    .description(enumSchema.description())
                    .build();
        };
    }

    /// @param request The model's call of a tool.
    /// @return It as a [ToolCall].
    public static ToolCall toolCall(final ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments());
    }

    /// Makes the langchain4j executor of a tool, for the chat. langchain4j gives an executor the chat's id as its
    /// memory id, from which the call's context is made.
    ///
    /// @param tool     The tool.
    /// @param contexts Makes the context of a call from its memory id, or gives null if the memory id isn't a
    ///                 chat's.
    /// @return An executor that runs the tool, and gives a failure as text rather than throwing. Its
    /// [StroomToolExecutor#result] gives the whole [ToolResult], and langchain4j's `execute` only its text.
    public static StroomToolExecutor executor(final StroomTool tool,
                                              final Function<Object, ToolCallContext> contexts) {
        return new StroomToolExecutor(tool, contexts);
    }

    private static JsonObjectSchema objectSchema(final ObjectSchema schema) {
        final JsonObjectSchema.Builder builder = JsonObjectSchema.builder()
                .description(schema.description());
        schema.properties().forEach((name, property) -> builder.addProperty(name, schemaElement(property)));
        if (schema.required() != null) {
            builder.required(schema.required());
        }
        return builder.build();
    }

    /// The langchain4j executor of a [StroomTool], for the chat. langchain4j is given the result's text, which is
    /// all the chat's model sees; the agent loop (`McpAgentChat`) takes the whole result with [#result], for the
    /// user actions that go with it.
    public static final class StroomToolExecutor implements ToolExecutor {

        private final StroomTool tool;
        private final Function<Object, ToolCallContext> contexts;

        private StroomToolExecutor(final StroomTool tool, final Function<Object, ToolCallContext> contexts) {
            this.tool = tool;
            this.contexts = contexts;
        }

        /// Runs the tool.
        ///
        /// @param request  The model's call.
        /// @param memoryId The memory id langchain4j gives an executor: the chat's id, in a chat.
        /// @return What the tool returned, or a failure as an error result rather than throwing.
        public ToolResult result(final ToolExecutionRequest request, final Object memoryId) {
            final String name = tool.definition().name();
            try {
                final ToolCallContext context = contexts.apply(memoryId);
                if (context == null) {
                    return ToolResult.error("Error: " + name + " can only be used in a chat.");
                }
                return Objects.requireNonNullElseGet(tool.execute(toolCall(request), context),
                        () -> ToolResult.text(""));
            } catch (final ToolSessionException e) {
                // The tool needs something the session doesn't have, e.g. a chat's history.
                return ToolResult.error("Error: " + e.getMessage());
            } catch (final AiChatRunManager.WaitingForUserException e) {
                // The run stops until the user answers (e.g. ask_user's questions), and makes the call again then.
                throw e;
            } catch (final RuntimeException e) {
                // Not one of the tool's own errors (which it gives as its result), so a fault to look into.
                LOGGER.warn(() -> "Built-in tool '" + name + "' failed", e);
                return ToolResult.error("Error running built-in tool '" + name + "': "
                                        + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
            }
        }

        /// Runs the tool, for langchain4j.
        ///
        /// @param request  The model's call.
        /// @param memoryId The memory id langchain4j gives an executor: the chat's id, in a chat.
        /// @return The text of what the tool returned (see [#result]).
        @Override
        public String execute(final ToolExecutionRequest request, final Object memoryId) {
            return result(request, memoryId).text();
        }
    }
}
