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

package stroom.ai.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Fixes, in a reference file, the audit records each built-in tool makes when run on its fixed input in
/// [BuiltInToolWorld] (as a user with every permission): each call to [AiAuditLog], and each event it logs through
/// the event logging service (its type, description, and the event's action, objects, data and outcome). Also the
/// central tool call record the agent loop makes for every call, whether it ran, failed, was not allowed or was a
/// tool that doesn't exist.
class TestToolAuditEvents {

    private static final String REFERENCE = "permissions-audit-consent/tool-audit-events.txt";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .serializationInclusion(JsonInclude.Include.NON_EMPTY)
            .build();

    @TempDir
    Path tempDir;

    @Test
    void eachToolRecordsWhatItDid() {
        final StringBuilder sb = new StringBuilder();
        sb.append("""
                # The audit records each built-in tool makes, run on its fixed input as a user with every
                # permission. 'audit' lines are calls to AiAuditLog; 'event' lines are what it logs.
                """);
        final List<String> tools;
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            tools = world.toolNames();
        }
        for (final String tool : tools) {
            try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
                world.prepareFor(tool);
                world.run(tool, BuiltInToolWorld.ARGUMENTS.getOrDefault(tool, "{}"));
                sb.append("\n## ").append(tool).append(' ')
                        .append(BuiltInToolWorld.ARGUMENTS.getOrDefault(tool, "{}")).append('\n');
                appendRecorded(sb, world);
            }
        }
        GoldenFiles.assertMatches(REFERENCE.replace(".txt", "-per-tool.txt"), sb.toString());
    }

    @Test
    void theAgentLoopRecordsEveryToolCall() {
        final StringBuilder sb = new StringBuilder();
        sb.append("""
                # The audit records the agent loop makes for one step of tool calls: a built-in tool that runs, one
                # whose result is an error, an MCP tool that runs, one that is not allowed, and a tool that doesn't
                # exist. The tools' own records (see tool-audit-events-per-tool.txt) are included in order.
                """);
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final FakeMcpServer server = new FakeMcpServer("trusted", true, List.of(), List.of(
                    FakeMcpServer.tool("find_docs", true, false, false, false),
                    FakeMcpServer.tool("update_doc", false, false, false, false)));
            final McpToolset toolset = FakeMcpServer.toolset(server);
            final ToolConsent toolConsent = Mockito.mock(ToolConsent.class);
            Mockito.when(toolConsent.decide(Mockito.anyInt(), Mockito.anyString(), Mockito.any(),
                    Mockito.any(), Mockito.any(), Mockito.anyBoolean())).thenReturn(ToolConsent.Verdict.RUN);
            Mockito.when(toolConsent.decide(Mockito.anyInt(), Mockito.eq("update_doc"), Mockito.any(),
                    Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(ToolConsent.Verdict.deny("You chose not to allow this tool in this chat."));
            final McpAgentChat agentChat = world.agentChat(toolConsent, Mockito.mock(AiChatRunManager.class));
            final BuiltInToolWorld.ScriptedModel model = new BuiltInToolWorld.ScriptedModel(List.of(List.of(
                    call("c1", "document_info", "{\"uuid\":\"xslt-uuid\"}"),
                    call("c2", "jq", "{\"path\":\"/outputs/missing.json\",\"query\":\".\"}"),
                    call("c3", "find_docs", "{\"query\":\"feeds\"}"),
                    call("c4", "update_doc", "{\"uuid\":\"x\"}"),
                    call("c5", "made_up_tool", "{}"))));

            BuiltInToolWorld.chat(agentChat, model, toolset);

            appendRecorded(sb, world);
            assertThat(world.auditLogCalls().stream()
                    .filter(invocation -> "toolCall".equals(invocation.getMethod().getName()))
                    .count())
                    .isEqualTo(5);
        }
        GoldenFiles.assertMatches(REFERENCE.replace(".txt", "-agent-loop.txt"), sb.toString());
    }

    private static ToolExecutionRequest call(final String id, final String name, final String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }

    private static void appendRecorded(final StringBuilder sb, final BuiltInToolWorld world) {
        final List<Invocation> audits = world.auditLogCalls();
        final List<Invocation> events = world.loggedEvents();
        if (audits.isEmpty() && events.isEmpty()) {
            sb.append("(nothing recorded)\n");
            return;
        }
        for (final Invocation audit : audits) {
            sb.append("- audit: ").append(audit.getMethod().getName()).append('(')
                    .append(world.normalise(arguments(audit.getArguments()))).append(")\n");
        }
        for (final Invocation event : events) {
            final Object[] args = event.getArguments();
            sb.append("- event: ").append(args[0]).append(" \"").append(world.normalise(String.valueOf(args[1])))
                    .append("\"\n");
            sb.append("  ").append(args[2] == null
                    ? "null"
                    : args[2].getClass().getSimpleName()).append(' ')
                    .append(world.normalise(json(args[2])).replace("\n", "\n  ")).append('\n');
        }
    }

    private static String arguments(final Object[] arguments) {
        final List<String> rendered = new ArrayList<>();
        for (final Object argument : arguments) {
            rendered.add(render(argument));
        }
        return String.join(", ", rendered);
    }

    private static String render(final Object value) {
        if (value instanceof final String text) {
            return "\"" + text.replace("\n", "\\n") + "\"";
        }
        if (value instanceof final Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> render(entry.getKey()) + "=" + render(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof final Collection<?> collection) {
            return collection.stream()
                    .map(TestToolAuditEvents::render)
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return String.valueOf(value);
    }

    private static String json(final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            return "(unable to write: " + e.getMessage() + ")";
        }
    }
}
