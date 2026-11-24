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

package stroom.ai.impl.tools.propose;

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.InstructionChecks;
import stroom.ai.impl.tools.instructions.InstructionChecks.Facts;
import stroom.ai.impl.tools.instructions.InstructionChecks.IndexFacts;
import stroom.ai.impl.tools.instructions.InstructionChecks.PlanField;
import stroom.ai.impl.tools.instructions.InstructionDraft;
import stroom.ai.impl.tools.instructions.InstructionFacts;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestProposeInstructionsTool {

    private static final int CHAT = 3;
    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Acme");
    private static final DocRef AGENTS = new DocRef(DocumentationDoc.TYPE, "agents-uuid", AgentInstructions.DOC_NAME);
    private static final String SAVED = "# Standing instructions\n\n## Time\n\n- Times are UTC.\n";
    private static final String PROPOSED = "# Standing instructions\n\n## Time\n\n- Times are Australia/Sydney.\n";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private final List<Map<String, Object>> asked = new ArrayList<>();
    private ElicitResult answer;
    private ProposeInstructionsTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(documentAccess.findFolder("/Feeds/Acme")).thenReturn(Optional.of(FOLDER));
        // A document's path is the folder holding it: a folder's own is that, then its name.
        Mockito.when(documentAccess.path(FOLDER)).thenReturn("/Feeds");
        Mockito.when(documentAccess.node(FOLDER)).thenReturn(Optional.of(ExplorerNode.builder().docRef(FOLDER)
                .name("Acme").build()));
        Mockito.when(documentAccess.folderPath(FOLDER)).thenCallRealMethod();
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of());
        tool = new ProposeInstructionsTool(workspace, documentAccess);
    }

    @Test
    void aChangeToTheFoldersDocIsOfferedAgainstItsSavedText() {
        withAgentsDoc(SAVED);

        final String result = run(Map.of("folder", "/Feeds/Acme", "edits", List.of(Map.of(
                "oldText", "Times are UTC.", "newText", "Times are Australia/Sydney."))));

        assertThat(result).contains("change to the AGENTS doc in folder '/Feeds/Acme'", "+1 −1 lines",
                "'Review and apply' button", "unsaved", "-- Times are UTC.", "+- Times are Australia/Sydney.");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_DOCUMENTATION_CHANGE)
                .containsEntry("doc", Map.of("type", DocumentationDoc.TYPE, "uuid", AGENTS.getUuid()))
                .containsEntry("request", PROPOSED)
                .containsEntry("base", SAVED)
                .containsEntry("base_hash", ProposeCodeChangeTool.sha256(SAVED))
                .containsEntry("added", 1)
                .containsEntry("removed", 1)
                .containsEntry("summary", "Times as the user chose in chats 4 and 9."));
    }

    @Test
    void aFolderWithNoDocIsOfferedANewOne() {
        final String result = run(Map.of("folder", "/Feeds/Acme", "markdown", PROPOSED));

        assertThat(result).contains("new AGENTS doc for folder '/Feeds/Acme'", "creates the doc and saves it");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.CREATE_INSTRUCTIONS)
                .containsEntry("doc", Map.of("type", ExplorerConstants.FOLDER_TYPE, "uuid", FOLDER.getUuid()))
                .containsEntry("request", PROPOSED)
                .containsEntry("added", 5)
                .doesNotContainKey("base"));
    }

    @Test
    void editsNeedADocToEdit() {
        final String result = run(Map.of("folder", "/Feeds/Acme", "edits", List.of(Map.of(
                "oldText", "a", "newText", "b"))));

        assertThat(result).contains("has no AGENTS doc, so there is nothing to edit");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aTypedSectionLineTheToolsWouldLeaveOutIsPointedOutWithItsFix() {
        final String result = run(Map.of("folder", "/Feeds/Acme", "markdown",
                "## Required imports\n\n- translations: Stream-Meta-V1\n- indexng: Common-Index-V1\n"));

        assertThat(result).contains("Its checks (as check_instructions gives them; if a problem is a slip, propose "
                                    + "the corrected text again",
                        "- Problem (unknown_key): '## Required imports' line '- indexng: Common-Index-V1' names no "
                        + "stage the tools know, so they leave it out. Fix: replace it with '- indexing: "
                        + "Common-Index-V1'.",
                        "The text with the problems' fixes applied:\n## Required imports\n\n- translations: "
                        + "Stream-Meta-V1\n- indexing: Common-Index-V1\n")
                .doesNotContain("translations: Stream-Meta-V1' names");
        // Still offered: the user decides.
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void aProposalIsCheckedAgainstTheIndexesWhereItWouldApply() {
        withFacts(new Facts(List.of(), List.of(new IndexFacts("Endpoint", "/Feeds/Acme", Set.of("actor.login"),
                Map.of(), Map.of()))));

        final String result = run(Map.of("folder", "/Feeds/Acme", "markdown", "## Notes\n- user: actor.login\n"));

        assertThat(result).contains("- Problem (misplaced_fields): '- user: actor.login' under '## Notes'",
                "The text with the problems' fixes applied:\n## Notes\n\n## Fields\n\n- user: actor.login\n");
    }

    @Test
    void aCleanProposalSaysNothingOfChecks() {
        assertThat(run(Map.of("folder", "/Feeds/Acme", "markdown", PROPOSED))).doesNotContain("Its checks");
    }

    @Test
    void theSameTextIsNothingToPropose() {
        withAgentsDoc(SAVED);

        assertThat(run(Map.of("folder", "/Feeds/Acme", "markdown", SAVED))).contains("nothing to propose");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aDraftUsesWhatTheUserSaidAndTheIndexPlansAskingOnlyWhereTheyDiffer() {
        withFacts(new Facts(List.of(), List.of(
                new IndexFacts("Endpoint", "/Feeds/Acme", Set.of("actor.login", "user.name", "host.name"), Map.of(
                        InstructionRules.USER_FIELD, new PlanField("actor.login", "Endpoint indexing"),
                        InstructionRules.HOST_FIELD, new PlanField("host.name", "Endpoint indexing")), Map.of()),
                new IndexFacts("Proxy", "/Feeds/Acme/Proxy", Set.of("UserId", "user.name"), Map.of(
                        InstructionRules.USER_FIELD, new PlanField("UserId", "Proxy indexing")), Map.of(
                        InstructionRules.USER_FIELD, "user.name")))));
        answer = new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("q1", "UserId"));

        final String result = runDraft(Map.of("folder", "/Feeds/Acme", "action", "draft", "fields",
                List.of("user: actor.login")));

        // Endpoint's user was said; its host is the plan's; Proxy's plan and the convention differ, so it is asked.
        assertThat(asked).singleElement().satisfies(form -> assertThat(form.toString())
                .contains("Index Proxy: which field holds the user?", "UserId (the index plan of 'Proxy indexing')",
                        "user.name (a field convention)", InstructionDraft.LEAVE_OUT));
        assertThat(result).startsWith("A draft of the AGENTS doc in /Feeds/Acme (a new doc): - user: actor.login, "
                                      + "UserId; - host: host.name.\nIts checks:\nNo problems or suggestions.")
                .endsWith("# Standing instructions\n\n## Fields\n\n- user: actor.login, UserId\n- host: "
                          + "host.name\n");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aDraftChangesTheLinesOfTheFoldersDoc() {
        withAgentsDoc(SAVED + "\n## Fields\n- user: UserId\n");
        withFacts(new Facts(List.of(new InstructionDoc(AGENTS, "/Feeds/Acme", SAVED + "\n## Fields\n- user: "
                                                                               + "UserId\n", List.of())),
                List.of(new IndexFacts("Endpoint", "/Feeds/Acme", Set.of("actor.login", "UserId"), Map.of(),
                        Map.of()))));

        final String result = runDraft(Map.of("folder", "/Feeds/Acme", "action", "draft", "fields",
                List.of("user: actor.login")));

        assertThat(result).contains("(its doc with the lines changed): - user: actor.login.")
                .endsWith(SAVED + "\n## Fields\n- user: actor.login\n");
    }

    @Test
    void withoutFormsADraftGivesTheQuestionsToAskInTheChat() {
        withFacts(new Facts(List.of(), List.of(new IndexFacts("Endpoint", "/Feeds/Acme", Set.of("actor.login"),
                Map.of(), Map.of()))));

        final String result = runDraft(Map.of("folder", "/Feeds/Acme", "action", "draft", "keys", List.of("user")));

        assertThat(result).isEqualTo("The user can't be asked in a form now. Ask them in the chat, then draft again "
                                     + "with fields ('key: field' each) from their answers:\n- Which field holds the "
                                     + "user? (as the indexes name it; blank to leave it out)");
    }

    @Test
    void aDraftRefusesAKeyTheToolsDontKnow() {
        withFacts(new Facts(List.of(), List.of()));

        assertThat(runDraft(Map.of("folder", "/Feeds/Acme", "action", "draft", "fields", List.of("usr: x"))))
                .startsWith("Error: 'usr: x' isn't 'key: field'");
        assertThat(runDraft(Map.of("folder", "/Feeds/Acme", "action", "draft", "keys", List.of("nope"))))
                .isEqualTo("Error: 'nope' is no key the tools know.");
        assertThat(runDraft(Map.of("folder", "/Feeds/Acme", "action", "draft")))
                .startsWith("Nothing to draft for /Feeds/Acme");
    }

    @Test
    void aFolderTheUserCantSeeIsRefused() {
        assertThat(run(Map.of("folder", "/Secret", "markdown", PROPOSED))).startsWith("Error: no folder '/Secret'");
        assertThat(takeUserActions()).isEmpty();
    }

    private void withAgentsDoc(final String text) {
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of(ExplorerNode.builder()
                .docRef(AGENTS).build()));
        Mockito.when(documentAccess.read(AGENTS, DocumentationDoc.class)).thenReturn(Optional.of(
                DocumentationDoc.builder().uuid(AGENTS.getUuid()).name(AGENTS.getName()).data(text).build()));
    }

    private void withFacts(final Facts facts) {
        final InstructionFacts instructionFacts = Mockito.mock(InstructionFacts.class);
        Mockito.when(instructionFacts.docs(Mockito.any(), Mockito.anyString())).thenReturn(facts.docs());
        Mockito.when(instructionFacts.gather(Mockito.anyList(), Mockito.anyString())).thenReturn(facts);
        Mockito.when(instructionFacts.checkDraft(Mockito.any(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString())).thenAnswer(invocation -> InstructionChecks.check(invocation.getArgument(2),
                invocation.getArgument(3), facts, true));
        tool.setInstructionFacts(instructionFacts);
    }

    // A draft call, in a chat whose user answers a form with the answer set (none: no form can be shown).
    private String runDraft(final Map<String, Object> arguments) {
        final ToolCallContext inChat = ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(new SessionWorkspace(workspace, CHAT))
                .runContext(context)
                .userQuestions(new UserQuestions() {
                    @Override
                    public boolean canAsk() {
                        return answer != null;
                    }

                    @Override
                    public ElicitResult ask(final String toolName,
                                            final String callId,
                                            final String intro,
                                            final Map<String, Object> form) {
                        asked.add(form);
                        return answer;
                    }
                })
                .build();
        final ToolResult result = context.run(() -> tool.execute(new ToolCall("c1", ProposeInstructionsTool.NAME,
                JsonUtil.writeValueAsString(arguments)), inChat));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }

    private String run(final Map<String, Object> arguments) {
        final Map<String, Object> withSummary = new HashMap<>(arguments);
        withSummary.put("summary", "Times as the user chose in chats 4 and 9.");
        final ToolResult result = context.run(() -> tool.execute(
                new ToolCall("c1", ProposeInstructionsTool.NAME, JsonUtil.writeValueAsString(withSummary)),
                ToolCalls.inChat(CHAT, workspace)));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }

    // The user actions offered by the calls since this was last called, as the chat shows them.
    private List<Map<String, Object>> takeUserActions() {
        final List<Map<String, Object>> taken = List.copyOf(offered);
        offered.clear();
        return taken;
    }
}
