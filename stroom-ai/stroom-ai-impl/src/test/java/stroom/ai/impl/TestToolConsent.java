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

import stroom.ai.impl.AiToolApprovalDao.Approval;
import stroom.ai.impl.ToolConsent.Kind;
import stroom.ai.impl.ToolConsent.Verdict;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.openai.shared.McpToolPolicy;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolConsent {

    private final AiToolApprovalDao approvalDao = Mockito.mock(AiToolApprovalDao.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final BuiltInTools builtInTools = Mockito.mock(BuiltInTools.class);
    private final McpToolset toolset = Mockito.mock(McpToolset.class);
    private AiConfig aiConfig = new AiConfig();
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final ToolConsent consent = new ToolConsent(approvalDao, aiDao, () -> aiConfig, () -> runManager);

    {
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of(
                ToolSpecification.builder().name("read_file").build(), (request, memoryId) -> ""));
        Mockito.when(builtInTools.isReadOnly("read_file")).thenReturn(true);
        Mockito.when(approvalDao.find(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void testBuiltInToolsRun() {
        assertThat(decide("read_file", false).kind()).isEqualTo(Kind.RUN);
        assertThat(decide("read_file", true).kind()).isEqualTo(Kind.RUN);
    }

    @Test
    void testBuiltInToolsThatChangeTheWorkspaceRunWhileTheUserIsAway() {
        Mockito.when(builtInTools.toolMap()).thenReturn(Map.of(
                ToolSpecification.builder().name("write_file").build(), (request, memoryId) -> ""));
        Mockito.when(builtInTools.isReadOnly("write_file")).thenReturn(false);

        // They only reach the chat's own workspace and memory, which have their own controls.
        assertThat(decide("write_file", false).kind()).isEqualTo(Kind.RUN);
        assertThat(decide("write_file", true).kind()).isEqualTo(Kind.RUN);
    }

    @Test
    void testToolsThatConfirmThemselvesAreNotAskedAboutTwice() {
        tool(info(true, false, true, null, true, null));

        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
        // Its own form waits for the user, so it can run while they are away.
        assertThat(decide("s__t", true).kind()).isEqualTo(Kind.RUN);
    }

    @Test
    void testToolsThatConfirmThemselvesStillAskOnUntrustedServersOrWhenTheirPolicySays() {
        tool(info(false, false, null, null, true, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);

        tool(info(true, false, null, null, true, McpToolPolicy.ASK_ALWAYS));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
    }

    @Test
    void testUntrustedServersAlwaysAsk() {
        tool(info(false, true, null, null, null));

        final Verdict verdict = decide("s__t", false);

        assertThat(verdict.kind()).isEqualTo(Kind.ASK);
        assertThat(verdict.reason()).contains("not trusted");
        assertThat(verdict.risks()).contains("untrusted server");
        assertThat(verdict.allowForChat()).isTrue();
        // It says it is read-only, so the user may allow the server's read-only tools.
        assertThat(verdict.allowServerReadOnly()).isTrue();
    }

    @Test
    void testTrustedReadOnlyToolsRun() {
        tool(info(true, true, null, null, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
        // Even read-only, a destructive or open-world tool asks.
        tool(info(true, true, true, null, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
        tool(info(true, true, null, true, null));
        assertThat(decide("s__t", false).reason()).contains("reaches outside Stroom");
    }

    @Test
    void testPolicies() {
        tool(info(false, false, null, null, McpToolPolicy.ALLOW));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);

        tool(info(true, true, null, null, McpToolPolicy.ASK_ALWAYS));
        final Verdict askAlways = decide("s__t", false);
        assertThat(askAlways.kind()).isEqualTo(Kind.ASK);
        assertThat(askAlways.allowForChat()).isFalse();
        // A chat approval does not count for ASK_ALWAYS.
        Mockito.when(approvalDao.find(1, "s", "t"))
                .thenReturn(Optional.of(new Approval(9, "s", "t", "hash", AiToolApprovalDecision.ALLOW)));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
    }

    @Test
    void testChatApprovals() {
        tool(info(false, false, null, null, null));
        Mockito.when(approvalDao.find(1, "s", "t"))
                .thenReturn(Optional.of(new Approval(9, "s", "t", "hash", AiToolApprovalDecision.ALLOW)));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);

        Mockito.when(approvalDao.find(1, "s", "t"))
                .thenReturn(Optional.of(new Approval(9, "s", "t", "hash", AiToolApprovalDecision.DENY)));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.DENY);

        // A changed tool is asked about again, and its old approval removed.
        Mockito.when(approvalDao.find(1, "s", "t"))
                .thenReturn(Optional.of(new Approval(9, "s", "t", "old-hash", AiToolApprovalDecision.ALLOW)));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
        Mockito.verify(approvalDao).delete(9);
    }

    @Test
    void testServerReadOnlyApproval() {
        Mockito.when(approvalDao.find(1, "s", AiToolApprovalDao.ALL_READ_ONLY_TOOLS))
                .thenReturn(Optional.of(new Approval(8, "s", "*", "server-hash", AiToolApprovalDecision.ALLOW)));

        tool(info(false, true, null, null, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
        // Never covers a tool that can change things.
        tool(info(false, false, null, null, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
    }

    @Test
    void testReadOnlyToolsAskAfterDataIsReadOnlyWhenAnAdministratorSaysSo() {
        tool(info(true, true, null, null, null));
        Mockito.when(runManager.isTainted(1)).thenReturn(true);

        // By default, a trusted server's read-only tool runs even after untrusted data has been read.
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
        assertThat(decide("s__t", true).kind()).isEqualTo(Kind.RUN);

        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                true);
        final Verdict verdict = decide("s__t", false);
        assertThat(verdict.kind()).isEqualTo(Kind.ASK);
        assertThat(verdict.reason()).isEqualTo(ToolConsent.DATA_WAS_READ_REASON);
        // The user can allow it for the rest of the chat.
        assertThat(verdict.allowForChat()).isTrue();
        // While the user is away it waits for them.
        assertThat(decide("s__t", true).kind()).isEqualTo(Kind.ASK);

        // Nothing untrusted has been read, so it runs.
        Mockito.when(runManager.isTainted(1)).thenReturn(false);
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
    }

    @Test
    void testAfterDataIsReadOnlyApprovalsOfTheToolItselfCount() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                true);
        Mockito.when(runManager.isTainted(1)).thenReturn(true);
        tool(info(true, true, null, null, null));

        // Allowing all the server's read-only tools doesn't cover this.
        Mockito.when(approvalDao.find(1, "s", AiToolApprovalDao.ALL_READ_ONLY_TOOLS))
                .thenReturn(Optional.of(new Approval(8, "s", "*", "server-hash", AiToolApprovalDecision.ALLOW)));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);

        // Allowing this tool for the chat does.
        Mockito.when(approvalDao.find(1, "s", "t"))
                .thenReturn(Optional.of(new Approval(9, "s", "t", "hash", AiToolApprovalDecision.ALLOW)));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
    }

    @Test
    void testApproveEverything() {
        Mockito.when(aiDao.isApproveEverything(1)).thenReturn(true);
        tool(info(true, false, null, null, null));
        // Not allowed by the administrator.
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);

        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, true, null, null, null,
                null);
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.RUN);
        // Never untrusted, destructive or open-world tools (S4).
        tool(info(false, false, null, null, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
        tool(info(true, false, true, null, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
        tool(info(true, false, null, true, null));
        assertThat(decide("s__t", false).kind()).isEqualTo(Kind.ASK);
    }

    @Test
    void testWhileTheUserIsAwayOnlyTrustedReadOnlyToolsRun() {
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, true, null, null, null,
                null);
        Mockito.when(aiDao.isApproveEverything(1)).thenReturn(true);
        Mockito.when(approvalDao.find(1, "s", "t"))
                .thenReturn(Optional.of(new Approval(9, "s", "t", "hash", AiToolApprovalDecision.ALLOW)));

        tool(info(true, true, null, null, null));
        assertThat(decide("s__t", true).kind()).isEqualTo(Kind.RUN);
        // ALLOW policies, chat approvals and approve-everything do not apply (S3).
        tool(info(true, false, null, null, McpToolPolicy.ALLOW));
        assertThat(decide("s__t", true).kind()).isEqualTo(Kind.ASK);
        assertThat(decide("s__t", true).reason()).contains("You were away");
        tool(info(true, false, null, null, null));
        assertThat(decide("s__t", true).kind()).isEqualTo(Kind.ASK);
    }

    @Test
    void testResourceTools() {
        Mockito.when(toolset.toolInfo("list_resources")).thenReturn(Optional.empty());
        Mockito.when(toolset.isReadOnly("list_resources")).thenReturn(true);
        assertThat(decide("list_resources", false).kind()).isEqualTo(Kind.RUN);
    }

    @Test
    void testHash() {
        assertThat(McpToolInfo.hash("a", "b")).hasSize(64).isEqualTo(McpToolInfo.hash("a", "b"));
        assertThat(McpToolInfo.hash("a", "b")).isNotEqualTo(McpToolInfo.hash("ab", ""));
    }

    private Verdict decide(final String tool, final boolean unattended) {
        return consent.decide(1, tool, builtInTools, toolset, unattended);
    }

    private void tool(final McpToolInfo info) {
        Mockito.when(toolset.toolInfo("s__t")).thenReturn(Optional.of(info));
    }

    private static McpToolInfo info(final boolean trusted,
                                    final boolean readOnly,
                                    final Boolean destructive,
                                    final Boolean openWorld,
                                    final McpToolPolicy policy) {
        return info(trusted, readOnly, destructive, openWorld, false, policy);
    }

    private static McpToolInfo info(final boolean trusted,
                                    final boolean readOnly,
                                    final Boolean destructive,
                                    final Boolean openWorld,
                                    final boolean confirms,
                                    final McpToolPolicy policy) {
        return new McpToolInfo("s__t", "s", "t", trusted, readOnly, destructive, openWorld, confirms, policy,
                "hash", "server-hash");
    }
}
