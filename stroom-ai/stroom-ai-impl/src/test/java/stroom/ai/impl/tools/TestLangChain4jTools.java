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
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.ai.shared.AiChatRunStopReason;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLangChain4jTools {

    @Test
    void aDefinitionBecomesTheSpecificationLangchain4jWouldBuild() {
        final ToolDefinition definition = ToolDefinition.builder()
                .name("t")
                .description("Does things.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty("path", "The file.")
                        .addIntegerProperty("limit", "The most.")
                        .addBooleanProperty("all", "Everything.")
                        .addEnumProperty("mode", List.of("a", "b"), "How.")
                        .addProperty("items", ArraySchema.builder()
                                .items(ObjectSchema.builder()
                                        .addStringProperty("name", "Its name.")
                                        .required("name")
                                        .build())
                                .description("The items.")
                                .build())
                        .addProperty("tags", ArraySchema.builder()
                                .items(StringSchema.builder().build())
                                .description("The tags.")
                                .build())
                        .addProperty("namespaces", ObjectSchema.builder().description("Prefixes.").build())
                        .required("path", "mode")
                        .build())
                .build();
        final ToolSpecification expected = ToolSpecification.builder()
                .name("t")
                .description("Does things.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "The file.")
                        .addIntegerProperty("limit", "The most.")
                        .addBooleanProperty("all", "Everything.")
                        .addEnumProperty("mode", List.of("a", "b"), "How.")
                        .addProperty("items", JsonArraySchema.builder()
                                .items(JsonObjectSchema.builder()
                                        .addStringProperty("name", "Its name.")
                                        .required("name")
                                        .build())
                                .description("The items.")
                                .build())
                        .addProperty("tags", JsonArraySchema.builder()
                                .items(JsonStringSchema.builder().build())
                                .description("The tags.")
                                .build())
                        .addProperty("namespaces", JsonObjectSchema.builder().description("Prefixes.").build())
                        .required("path", "mode")
                        .build())
                .build();

        final ToolSpecification specification = LangChain4jTools.specification(definition);

        assertThat(specification).isEqualTo(expected);
        assertThat(specification.parameters().properties().keySet())
                .containsExactly("path", "limit", "all", "mode", "items", "tags", "namespaces");
    }

    @Test
    void aDefinitionWithoutParametersHasNone() {
        final ToolSpecification specification = LangChain4jTools.specification(ToolDefinition.builder()
                .name("t")
                .description("Does things.")
                .build());

        assertThat(specification).isEqualTo(ToolSpecification.builder().name("t").description("Does things.")
                .build());
        assertThat(specification.parameters()).isNull();
    }

    @Test
    void anObjectWithoutRequiredPropertiesNamesNone() {
        final ToolSpecification specification = LangChain4jTools.specification(ToolDefinition.builder()
                .name("t")
                .parameters(ObjectSchema.builder().addStringProperty("a", "A.").build())
                .build());

        assertThat(specification.parameters())
                .isEqualTo(JsonObjectSchema.builder().addStringProperty("a", "A.").build());
    }

    @Test
    void aRequestBecomesACall() {
        assertThat(LangChain4jTools.toolCall(request("{\"a\":1}")))
                .isEqualTo(new ToolCall("c1", "t", "{\"a\":1}"));
    }

    @Test
    void theExecutorRunsTheToolInTheChatsContext() {
        final ToolExecutor executor = executor((call, context) -> call.arguments() + " in "
                                                                  + context.session().id());

        assertThat(executor.execute(request("{}"), 4)).isEqualTo("{} in 4");
    }

    @Test
    void theExecutorRefusesACallOutsideAChat() {
        final ToolExecutor executor = executor((call, context) -> "ran");

        assertThat(executor.execute(request("{}"), null)).isEqualTo("Error: t can only be used in a chat.");
    }

    @Test
    void theExecutorGivesFailuresAsText() {
        assertThat(executor((call, context) -> {
            throw new ToolSessionException("t needs the chat's history.");
        }).execute(request("{}"), 4)).isEqualTo("Error: t needs the chat's history.");
        assertThat(executor((call, context) -> {
            throw new IllegalStateException("kaboom");
        }).execute(request("{}"), 4)).isEqualTo("Error running built-in tool 't': kaboom");
        assertThat(executor((call, context) -> null).execute(request("{}"), 4)).isEmpty();
    }

    @Test
    void theExecutorLetsTheRunStopToWaitForTheUser() {
        // E.g. ask_user's questions: the run stops until they are answered, and makes the call again then.
        assertThatThrownBy(() -> executor((call, context) -> {
            throw new AiChatRunManager.WaitingForUserException("Questions need answers",
                    AiChatRunStopReason.NEEDS_ANSWERS);
        }).execute(request("{}"), 4)).isInstanceOf(AiChatRunManager.WaitingForUserException.class);
    }

    @Test
    void theExecutorGivesTheWholeResultToTheAgentLoopAndOnlyItsTextToLangChain4j() {
        final ToolUserAction action = new ToolUserAction("create-query-1", "create_query", Map.of("name", "n"));
        final ToolResult offered = ToolResult.text("offered").withUserAction(action);
        final LangChain4jTools.StroomToolExecutor executor = LangChain4jTools.executor(tool((call, context) ->
                offered), ToolCalls.chatContexts()::forMemoryId);

        assertThat(executor.result(request("{}"), 4)).isEqualTo(offered);
        assertThat(executor.execute(request("{}"), 4)).isEqualTo("offered");
        // Failures are error results.
        assertThat(executor.result(request("{}"), null).isError()).isTrue();
        assertThat(LangChain4jTools.executor(tool((call, context) -> {
            throw new IllegalStateException("kaboom");
        }), ToolCalls.chatContexts()::forMemoryId).result(request("{}"), 4))
                .satisfies(result -> {
                    assertThat(result.isError()).isTrue();
                    assertThat(result.text()).isEqualTo("Error running built-in tool 't': kaboom");
                    assertThat(result.userActions()).isEmpty();
                });
    }

    private static StroomTool tool(final BiFunction<ToolCall, ToolCallContext, ToolResult> run) {
        return new StroomTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder().name("t").build();
            }

            @Override
            public ToolResult execute(final ToolCall call, final ToolCallContext context) {
                return run.apply(call, context);
            }
        };
    }

    private static ToolExecutor executor(final BiFunction<ToolCall, ToolCallContext, String> run) {
        final StroomTool tool = new StroomTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder().name("t").build();
            }

            @Override
            public ToolResult execute(final ToolCall call, final ToolCallContext context) {
                final String text = run.apply(call, context);
                return text == null
                        ? null
                        : ToolResult.text(text);
            }
        };
        return LangChain4jTools.executor(tool, ToolCalls.chatContexts()::forMemoryId);
    }

    private static ToolExecutionRequest request(final String arguments) {
        return ToolExecutionRequest.builder().id("c1").name("t").arguments(arguments).build();
    }
}
