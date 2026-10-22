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

import stroom.ai.api.AiService;
import stroom.ai.impl.AiToolApprovalDao.Approval;
import stroom.ai.impl.ToolApprovalRequest.PendingCall;
import stroom.ai.impl.ToolApprovalRequest.Status;
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.StandInTools;
import stroom.ai.shared.AiApprovalScope;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Fixes, in a reference file, today's decisions about tool calls: what [ToolConsent] decides for each built-in
/// tool, stand-ins for the ported tools that change Stroom, and a representative set of MCP tools in each situation
/// (normal, the user away, after reading untrusted content, after reading an untrusted server's output, allowed or
/// denied earlier, allowed for a tool that has since changed, a server's read-only tools allowed, 'approve
/// everything'), with the approval buttons each
/// request offers; what [AiMemoryService] does with a change to memory in those situations; and what
/// [ToolApprovalService] records for each answer the user can give.
class TestConsentDecisions {

    private static final String REFERENCE = "permissions-audit-consent/consent-decisions.txt";
    private static final int CHAT_ID = BuiltInToolWorld.CHAT_ID;
    private static final String USER = "user1";

    @TempDir
    Path tempDir;

    @Test
    void decisionsPerToolAndSituation() {
        final StringBuilder sb = new StringBuilder();
        sb.append("""
                # Today's consent decisions. A verdict is RUN, DENY (reason) or ASK, with the buttons the approval
                # card offers (allow for chat, allow the server's read-only tools), the risks it shows, the reason
                # logged and the reason the card shows (when it differs).
                """);
        final Map<String, Consumer<Situation>> situations = situations();
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            final BuiltInTools builtInTools = world.builtInTools();
            sb.append("\n## Built-in tools\n");
            for (final String tool : world.toolNames()) {
                final List<String> verdicts = new ArrayList<>();
                for (final Map.Entry<String, Consumer<Situation>> situation : situations.entrySet()) {
                    verdicts.add(decide(builtInTools, McpToolset.EMPTY, tool, situation.getValue()));
                }
                final boolean same = verdicts.stream().distinct().count() == 1;
                sb.append("- ").append(tool)
                        .append(builtInTools.isReadOnly(tool)
                                ? " (read-only"
                                : " (changes things")
                        .append(builtInTools.readsUntrustedContent(tool)
                                ? ", reads untrusted content)"
                                : ", reads only trusted content)")
                        .append(": ")
                        .append(same
                                ? verdicts.getFirst() + " in every situation"
                                : String.join(" | ", verdicts))
                        .append('\n');
            }

            sb.append("\n## Stand-ins for Stroom's own tools that change Stroom (e.g. the build tools)\n");
            sb.append("The built-in tools above only change the chat's own things (its workspace files, and memory, "
                      + "which asks about a lasting change itself), so these stand in for the ported tools.\n");
            final BuiltInTools standIns = StandInTools.builtInTools(
                    StandInTools.writeTool("save_xslt"),
                    StandInTools.selfConfirmingWriteTool("create_feed"),
                    StandInTools.mixedTool("mixed_tool"),
                    StandInTools.readOnlyTool("read_only_tool"));
            final Map<String, String> standInDescriptions = new LinkedHashMap<>();
            standInDescriptions.put("save_xslt", "changes things, doesn't confirm itself");
            standInDescriptions.put("create_feed", "changes things, confirms each change itself");
            standInDescriptions.put("mixed_tool", "some calls change things, doesn't confirm itself; a call "
                                                  + "whose arguments aren't known");
            standInDescriptions.put("read_only_tool", "read-only");
            // Their approvals for the chat only count once the chat has read untrusted content, so those situations
            // are shown after it too.
            final Map<String, Consumer<Situation>> standInSituations = new LinkedHashMap<>(situations);
            standInSituations.put("after reading untrusted content, allowed for the chat earlier", situation -> {
                situation.tainted = true;
                situation.approval = AiToolApprovalDecision.ALLOW;
            });
            standInSituations.put("after reading untrusted content, denied for the chat earlier", situation -> {
                situation.tainted = true;
                situation.approval = AiToolApprovalDecision.DENY;
            });
            standInSituations.put("after reading untrusted content, allowed for the chat earlier, tool since changed",
                    situation -> {
                        situation.tainted = true;
                        situation.approval = AiToolApprovalDecision.ALLOW;
                        situation.approvalForChangedTool = true;
                    });
            for (final Map.Entry<String, String> standIn : standInDescriptions.entrySet()) {
                sb.append("\n### ").append(standIn.getKey()).append(" (").append(standIn.getValue()).append(")\n");
                for (final Map.Entry<String, Consumer<Situation>> situation : standInSituations.entrySet()) {
                    sb.append("- ").append(situation.getKey()).append(": ")
                            .append(decide(standIns, McpToolset.EMPTY, standIn.getKey(), situation.getValue()))
                            .append('\n');
                }
            }

            sb.append("\n## MCP tools\n");
            sb.append("Servers: 'trusted' (trusted; reprocess is ASK_ALWAYS, always_ok is ALLOW), 'untrusted'.\n");
            final McpToolset toolset = mcpToolset();
            final List<String> mcpTools = new ArrayList<>(toolset.tools(names(builtInTools)).keySet().stream()
                    .map(ToolSpecification::name)
                    .toList());
            mcpTools.add("not_a_tool");
            for (final String tool : mcpTools) {
                sb.append("\n### ").append(tool).append(describe(toolset, tool)).append('\n');
                for (final Map.Entry<String, Consumer<Situation>> situation : situations.entrySet()) {
                    sb.append("- ").append(situation.getKey()).append(": ")
                            .append(decide(builtInTools, toolset, tool, situation.getValue())).append('\n');
                }
            }
        }
        sb.append("\n## Memory changes (AiMemoryService)\n");
        sb.append(memoryDecisions());
        sb.append("\n## Answers to a tool approval request (ToolApprovalService)\n");
        sb.append(approvalAnswers());
        GoldenFiles.assertMatches(REFERENCE, sb.toString());
    }

    // The situations, in order: each sets up the run, the approvals held and the configuration.
    private static Map<String, Consumer<Situation>> situations() {
        final Map<String, Consumer<Situation>> situations = new LinkedHashMap<>();
        situations.put("normal", situation -> {
        });
        situations.put("user away (unattended)", situation -> situation.unattended = true);
        situations.put("after reading untrusted content", situation -> situation.tainted = true);
        situations.put("after reading untrusted content, askReadOnlyToolsAfterUntrustedContent on", situation -> {
            situation.tainted = true;
            situation.askReadOnlyAfterUntrusted = true;
        });
        situations.put("after reading an untrusted server's output", situation ->
                situation.readUntrustedServer = true);
        situations.put("allowed for the chat earlier", situation -> situation.approval = AiToolApprovalDecision.ALLOW);
        situations.put("denied for the chat earlier", situation -> situation.approval = AiToolApprovalDecision.DENY);
        situations.put("allowed for the chat earlier, tool since changed", situation -> {
            situation.approval = AiToolApprovalDecision.ALLOW;
            situation.approvalForChangedTool = true;
        });
        situations.put("server's read-only tools allowed for the chat", situation -> situation.serverReadOnly = true);
        situations.put("'approve everything' on, after reading an untrusted server's output", situation -> {
            situation.readUntrustedServer = true;
            situation.approveEverything = true;
        });
        situations.put("'approve everything' on, after reading untrusted content", situation -> {
            situation.tainted = true;
            situation.approveEverything = true;
        });
        situations.put("user away, allowed for the chat earlier", situation -> {
            situation.unattended = true;
            situation.approval = AiToolApprovalDecision.ALLOW;
        });
        return situations;
    }

    private static String decide(final BuiltInTools builtInTools,
                                 final McpToolset toolset,
                                 final String tool,
                                 final Consumer<Situation> setUp) {
        final Situation situation = new Situation();
        setUp.accept(situation);
        final AiToolApprovalDao approvalDao = Mockito.mock(AiToolApprovalDao.class);
        final AiDao aiDao = Mockito.mock(AiDao.class);
        final AiConfig aiConfig = Mockito.mock(AiConfig.class);
        final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
        Mockito.when(aiConfig.isAskReadOnlyToolsAfterUntrustedContent())
                .thenReturn(situation.askReadOnlyAfterUntrusted);
        Mockito.when(aiConfig.isAllowApproveEverything()).thenReturn(situation.approveEverything);
        Mockito.when(aiDao.isApproveEverything(CHAT_ID)).thenReturn(situation.approveEverything);
        Mockito.when(runManager.isTainted(CHAT_ID)).thenReturn(situation.tainted);
        Mockito.when(runManager.hasReadUntrustedServer(CHAT_ID)).thenReturn(situation.readUntrustedServer);
        Mockito.when(runManager.isUnattended(CHAT_ID)).thenReturn(situation.unattended);
        Mockito.when(approvalDao.find(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.empty());
        // Stroom's own tools are approved for the chat under a server of their own.
        final String builtInHash = ToolConsent.builtInToolHash(builtInTools, tool);
        if (builtInHash != null && situation.approval != null) {
            Mockito.when(approvalDao.find(CHAT_ID, ToolConsent.BUILT_IN_SERVER_KEY, tool))
                    .thenReturn(Optional.of(new Approval(7, ToolConsent.BUILT_IN_SERVER_KEY, tool,
                            situation.approvalForChangedTool
                                    ? "an-older-hash"
                                    : builtInHash, situation.approval)));
        }
        toolset.toolInfo(tool).ifPresent(info -> {
            if (situation.approval != null) {
                Mockito.when(approvalDao.find(CHAT_ID, info.serverKey(), info.toolName()))
                        .thenReturn(Optional.of(new Approval(7, info.serverKey(), info.toolName(),
                                situation.approvalForChangedTool
                                        ? "an-older-hash"
                                        : info.hash(), situation.approval)));
            }
            if (situation.serverReadOnly) {
                Mockito.when(approvalDao.find(CHAT_ID, info.serverKey(), AiToolApprovalDao.ALL_READ_ONLY_TOOLS))
                        .thenReturn(Optional.of(new Approval(8, info.serverKey(),
                                AiToolApprovalDao.ALL_READ_ONLY_TOOLS, info.serverHash(),
                                AiToolApprovalDecision.ALLOW)));
            }
        });
        final ToolConsent toolConsent = new ToolConsent(approvalDao, aiDao, () -> aiConfig, () -> runManager);

        final ToolConsent.Verdict verdict = toolConsent.decide(CHAT_ID, tool, builtInTools, toolset,
                situation.unattended);

        final List<Integer> deleted = Mockito.mockingDetails(approvalDao).getInvocations().stream()
                .filter(invocation -> "delete".equals(invocation.getMethod().getName()))
                .map(invocation -> invocation.<Integer>getArgument(0))
                .toList();
        return describe(verdict) + (deleted.isEmpty()
                ? ""
                : " [the earlier approval is removed]");
    }

    private static String describe(final ToolConsent.Verdict verdict) {
        return switch (verdict.kind()) {
            case RUN -> "RUN";
            case DENY -> "DENY (" + verdict.reason() + ")";
            case ASK -> "ASK {allow for chat: " + yesNo(verdict.allowForChat())
                        + ", allow server's read-only tools: " + yesNo(verdict.allowServerReadOnly())
                        + ", risks: " + verdict.risks()
                        + ", reason: \"" + verdict.reason() + "\""
                        + (Objects.equals(verdict.reason(), verdict.cardReason())
                ? ""
                : ", card: " + (verdict.cardReason() == null
                        ? "none"
                        : "\"" + verdict.cardReason() + "\""))
                        + "}";
        };
    }

    private static String describe(final McpToolset toolset, final String tool) {
        return toolset.toolInfo(tool)
                .map(info -> " (server " + info.serverKey() + ", tool " + info.toolName()
                             + ", trusted " + yesNo(info.trusted())
                             + ", readOnlyHint " + info.readOnlyHint()
                             + ", destructiveHint " + info.destructiveHint()
                             + ", openWorldHint " + info.openWorldHint()
                             + ", confirms " + yesNo(info.confirms())
                             + ", policy " + info.policy()
                             + ", read-only for the chat " + yesNo(toolset.isReadOnly(tool)) + ")")
                .orElse(" (not a tool any server offers)");
    }

    private static McpToolset mcpToolset() {
        final FakeMcpServer trusted = new FakeMcpServer("trusted", true, List.of(
                new McpToolPolicyRule("reprocess", McpToolPolicy.ASK_ALWAYS),
                new McpToolPolicyRule("always_ok", McpToolPolicy.ALLOW)), List.of(
                FakeMcpServer.tool("find_docs", true, false, false, false),
                FakeMcpServer.tool("update_doc", false, false, false, false),
                FakeMcpServer.tool("delete_doc", false, true, false, false),
                FakeMcpServer.tool("fetch_url", true, false, true, false),
                FakeMcpServer.tool("create_feed", false, false, false, true),
                FakeMcpServer.tool("reprocess", false, false, false, false),
                FakeMcpServer.tool("always_ok", false, false, false, false),
                FakeMcpServer.tool("no_hints")));
        final FakeMcpServer untrusted = new FakeMcpServer("untrusted", false, List.of(), List.of(
                FakeMcpServer.tool("search", true, false, false, false),
                FakeMcpServer.tool("write", false, false, false, false),
                FakeMcpServer.tool("confirming_write", false, false, false, true)));
        return FakeMcpServer.toolset(trusted, untrusted);
    }

    private static Set<String> names(final BuiltInTools builtInTools) {
        return builtInTools.toolMap().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
    }

    // What becomes of a change to the user's memory and to the chat's, in each situation.
    private static String memoryDecisions() {
        final StringBuilder sb = new StringBuilder();
        final Map<String, boolean[]> situations = new LinkedHashMap<>();
        // {tainted, unattended}
        situations.put("normal", new boolean[]{false, false});
        situations.put("after reading untrusted content", new boolean[]{true, false});
        situations.put("user away (unattended)", new boolean[]{false, true});
        for (final Map.Entry<String, boolean[]> situation : situations.entrySet()) {
            for (final String path : List.of("/memories/prefs.md", "/memories/session/plan.md")) {
                final AiMemoryDao memoryDao = Mockito.mock(AiMemoryDao.class);
                final AiService aiService = Mockito.mock(AiService.class);
                final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
                final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
                Mockito.when(aiService.getChat(CHAT_ID)).thenReturn(new AiChat(CHAT_ID, 0L, 0L, USER, "chat"));
                Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn(USER);
                Mockito.when(runManager.isTainted(CHAT_ID)).thenReturn(situation.getValue()[0]);
                Mockito.when(runManager.isUnattended(CHAT_ID)).thenReturn(situation.getValue()[1]);
                Mockito.when(memoryDao.list(Mockito.anyString(), Mockito.any(), Mockito.anyString()))
                        .thenReturn(List.of());
                Mockito.when(memoryDao.get(Mockito.anyString(), Mockito.any(), Mockito.anyString(),
                        Mockito.anyString())).thenReturn(Optional.empty());
                Mockito.when(memoryDao.create(Mockito.anyString(), Mockito.any(), Mockito.anyString(),
                                Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                        .thenAnswer(invocation -> Optional.of(new AiMemory(1, 1, invocation.getArgument(1),
                                invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4),
                                0L, USER)));
                final AiMemoryService memoryService = Guice.createInjector(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(AiMemoryDao.class).toInstance(memoryDao);
                        bind(AiService.class).toInstance(aiService);
                        bind(AiChatRunManager.class).toInstance(runManager);
                        bind(ToolConsentGate.class).toInstance(ConsentGates.chat(null, aiService, runManager));
                        bind(SecurityContext.class).toInstance(securityContext);
                        bind(AiDao.class).toInstance(Mockito.mock(AiDao.class));
                    }
                }).getInstance(AiMemoryService.class);

                final String result = memoryService.write(CHAT_ID, path, "Times in UTC.", null);

                final List<String> stored = Mockito.mockingDetails(aiService).getInvocations().stream()
                        .filter(invocation -> "storeMessage".equals(invocation.getMethod().getName()))
                        .map(invocation -> invocation.getArgument(1) + " " + invocation.getArgument(2))
                        .toList();
                final boolean saved = Mockito.mockingDetails(memoryDao).getInvocations().stream()
                        .anyMatch(invocation -> "create".equals(invocation.getMethod().getName()));
                sb.append("- ").append(situation.getKey()).append(", write ").append(path).append(": \"")
                        .append(result).append("\"; saved: ").append(yesNo(saved)).append("; messages: ")
                        .append(stored).append('\n');
            }
        }
        return sb.toString();
    }

    // What each answer to a tool approval request records.
    private static String approvalAnswers() {
        final StringBuilder sb = new StringBuilder();
        final List<Answer> answers = List.of(
                new Answer("allow once", true, AiApprovalScope.ONCE, null, null),
                new Answer("allow for the chat", true, AiApprovalScope.CHAT, null, null),
                new Answer("allow the server's read-only tools", true, AiApprovalScope.SERVER_READ_ONLY, null, null),
                new Answer("deny", false, AiApprovalScope.ONCE, "Not that one.", null),
                new Answer("allow only the first call", true, AiApprovalScope.ONCE, null, List.of("call-1")));
        for (final boolean buttonsOffered : new boolean[]{true, false}) {
            for (final Answer answer : answers) {
                final AiService aiService = Mockito.mock(AiService.class);
                final AiDao aiDao = Mockito.mock(AiDao.class);
                final AiToolApprovalDao approvalDao = Mockito.mock(AiToolApprovalDao.class);
                final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
                Mockito.when(aiService.getChat(CHAT_ID)).thenReturn(new AiChat(CHAT_ID, 0L, 0L, USER, "chat"));
                Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn(USER);
                Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(),
                        Mockito.anyString())).thenReturn(true);
                final ToolApprovalService service = new ToolApprovalService(aiService, aiDao, approvalDao,
                        Mockito.mock(AiChatRunManager.class), securityContext);
                final List<PendingCall> calls = List.of(
                        new PendingCall("call-1", "trusted__update_doc", "trusted", "update_doc", "{}",
                                List.of("can modify"), "tool-hash", "server-hash", null, null),
                        new PendingCall("call-2", "trusted__find_docs", "trusted", "find_docs", "{}",
                                List.of("read-only"), "tool-hash-2", "server-hash", null, null));
                final AiChatMessage message = new AiChatMessage(50, CHAT_ID, System.currentTimeMillis(),
                        AiMessageType.APPROVAL_REQUEST, null, JsonUtil.writeValueAsString(new ToolApprovalRequest(
                        ToolApprovalRequest.KIND, Status.PENDING, "reason", calls, buttonsOffered, buttonsOffered),
                        false), 1, null);

                final Status status = service.answer(CHAT_ID, message, new AnswerAiApprovalRequest(null, 50,
                        answer.approved(), answer.scope(), answer.reason(), answer.callIds()));

                final String updated = Mockito.mockingDetails(aiDao).getInvocations().stream()
                        .filter(invocation -> "compareAndSetMessageText".equals(invocation.getMethod().getName()))
                        .map(invocation -> invocation.<String>getArgument(2))
                        .findFirst()
                        .orElseThrow();
                final ToolApprovalRequest after = JsonUtil.readValue(updated, ToolApprovalRequest.class);
                final List<String> saved = Mockito.mockingDetails(approvalDao).getInvocations().stream()
                        .filter(invocation -> "save".equals(invocation.getMethod().getName()))
                        .map(invocation -> invocation.getArgument(2) + "/" + invocation.getArgument(3) + " "
                                           + invocation.getArgument(5) + " (hash " + invocation.getArgument(4)
                                           + ")")
                        .toList();
                sb.append("- ").append(buttonsOffered
                                ? "buttons offered"
                                : "no buttons offered").append(", ").append(answer.label()).append(": status ")
                        .append(status).append("; calls ")
                        .append(after.calls().stream()
                                .map(call -> call.callId() + "=" + call.decision()
                                             + (call.note() == null
                                        ? ""
                                        : " (" + call.note() + ")"))
                                .toList())
                        .append("; remembered: ").append(saved).append('\n');
            }
        }
        // A request not answered within the expiry.
        final AiDao aiDao = Mockito.mock(AiDao.class);
        final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
        Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true);
        final AiService aiService = Mockito.mock(AiService.class);
        Mockito.when(aiService.getChat(CHAT_ID)).thenReturn(new AiChat(CHAT_ID, 0L, 0L, USER, "chat"));
        final ToolApprovalService service = new ToolApprovalService(aiService, aiDao,
                Mockito.mock(AiToolApprovalDao.class), runManager, Mockito.mock(SecurityContext.class));
        final AiChatMessage old = new AiChatMessage(51, CHAT_ID,
                System.currentTimeMillis() - ToolApprovalService.EXPIRY.toMillis() - 60_000,
                AiMessageType.APPROVAL_REQUEST, null, JsonUtil.writeValueAsString(new ToolApprovalRequest(
                ToolApprovalRequest.KIND, Status.PENDING, "reason", List.of(new PendingCall("call-1",
                "trusted__update_doc", "trusted", "update_doc", "{}", List.of(), "h", "s", null, null)), true,
                true), false), 1, null);
        final Status expired = service.answer(CHAT_ID, old, new AnswerAiApprovalRequest(null, 51, true,
                AiApprovalScope.CHAT, null, null));
        final boolean stopped = !Mockito.mockingDetails(runManager).getInvocations().isEmpty();
        sb.append("- answered after ").append(ToolApprovalService.EXPIRY).append(": status ").append(expired)
                .append("; the waiting run is stopped: ").append(yesNo(stopped)).append('\n');
        assertThat(expired).isEqualTo(Status.EXPIRED);
        return sb.toString();
    }

    private static String yesNo(final boolean value) {
        return value
                ? "yes"
                : "no";
    }

    /// An answer the user can give to a tool approval request.
    private record Answer(String label, boolean approved, AiApprovalScope scope, String reason, List<String> callIds) {

    }

    /// What holds when a call is decided.
    private static final class Situation {

        private boolean unattended;
        private boolean tainted;
        private boolean askReadOnlyAfterUntrusted;
        private boolean readUntrustedServer;
        private AiToolApprovalDecision approval;
        private boolean approvalForChangedTool;
        private boolean serverReadOnly;
        private boolean approveEverything;
    }
}
