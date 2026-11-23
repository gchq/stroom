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

package stroom.ai.impl.tools.skills;

import stroom.ai.api.AiService;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.ToolCallRecord;
import stroom.ai.impl.ToolResultRecord;
import stroom.ai.impl.UserActionRecord;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestProposeSkillTool {

    private static final int CHAT = 3;
    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Hunts");
    private static final DocRef SKILLS = new DocRef(DocumentationDoc.TYPE, "skills-uuid", SkillDoc.DOC_NAME);
    private static final String SKILL = """
            # Logons by user

            Counts a user's logons.

            ## When to use
            - "how many times did jsmith log on?"
            - "count a user's logons this week"

            ## Parameters
            - user (required): the account.
            - days (default 7): how far back.

            ## Steps
            1. Count them:
               ```json
               {"tool": "run_stroomql", "arguments": {"query": "from Logons where UserId = '{user}' and \
            EventTime > now() - {days}d select count()"}}
               ```

            ## Output
            A summary in the chat.
            """;

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final SkillFacts facts = Mockito.mock(SkillFacts.class);
    private final AiService aiService = Mockito.mock(AiService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final ToolRunContext runContext = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ElicitResult answer;
    private ProposeSkillTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(documentAccess.findFolder("/Hunts")).thenReturn(Optional.of(FOLDER));
        // A document's path is its folder's: the folders above it.
        Mockito.when(documentAccess.path(FOLDER)).thenReturn("/");
        Mockito.when(documentAccess.path(SKILLS)).thenReturn("/Hunts");
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of());
        Mockito.when(documentAccess.parent(SKILLS)).thenReturn(Optional.of(FOLDER));
        Mockito.when(facts.forSkill(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(SkillTestFacts.permissive());
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("u").subjectId("jsmith")
                .build());
        tool = new ProposeSkillTool(workspace, documentAccess, Mockito.mock(SkillCatalogue.class), facts,
                () -> aiService, securityContext);
    }

    @Test
    void aFolderWithNoSkillsDocIsOfferedANewOneWithItsHistory() {
        final String result = run(Map.of("folder", "/Hunts", "markdown", SKILL));

        assertThat(result).contains("new SKILLS doc for folder '/Hunts'", "creates the doc and saves it",
                "'Review and apply' button", "+stroom: skills", "+  - version: 1");
        final Map<String, Object> action = takeUserActions().getFirst();
        assertThat(action).containsEntry("action", UserActions.CREATE_INSTRUCTIONS)
                .containsEntry("name", SkillDoc.DOC_NAME)
                .containsEntry("doc", Map.of("type", ExplorerConstants.FOLDER_TYPE, "uuid", FOLDER.getUuid()))
                .doesNotContainKey("base");
        final SkillDoc proposed = SkillDoc.parse((String) action.get("request"));
        assertThat(proposed.isMarked()).isTrue();
        assertThat(proposed.skills()).extracting(SkillDoc.Skill::name).containsExactly("Logons by user");
        assertThat(proposed.latestEntry().orElseThrow()).satisfies(entry -> {
            assertThat(entry.author()).isEqualTo("jsmith");
            assertThat(entry.how()).isEqualTo(SkillDoc.HOW_AGENT);
            assertThat(entry.change()).isEqualTo("Created with 'Logons by user'");
            assertThat(entry.body()).isEqualTo(proposed.bodyHash());
        });
    }

    @Test
    void aChangeToTheFoldersSkillsDocIsADiffAgainstItsSavedText() {
        final String saved = SkillDoc.parse("").withSkill(SKILL, entry(1));
        withSkillsDoc(saved);

        final String result = run(Map.of("folder", "/Hunts", "markdown", SKILL.replace("Counts a user's logons.",
                "Counts a user's logons, by day."), "change", "By day"));

        assertThat(result).contains("in the skills doc 'SKILLS' in folder '/Hunts'", "unsaved",
                "refused if the doc has been saved with other text since", "+Counts a user's logons, by day.",
                "+  - version: 2", "+    change: \"By day\"");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_DOCUMENTATION_CHANGE)
                .containsEntry("doc", Map.of("type", DocumentationDoc.TYPE, "uuid", SKILLS.getUuid()))
                .containsEntry("base", saved)
                .containsEntry("base_hash", ProposeCodeChangeTool.sha256(saved))
                .containsEntry("summary", "By day"));
    }

    @Test
    void aDocNotNamedAsASkillsDocIsRefused() {
        final DocRef notes = new DocRef(DocumentationDoc.TYPE, "notes-uuid", "Notes");
        Mockito.when(documentAccess.findDocRef("notes-uuid")).thenReturn(Optional.of(notes));

        assertThat(run(Map.of("doc", "notes-uuid", "markdown", SKILL)))
                .startsWith("Error: 'Notes' isn't a skills doc");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aDraftsProblemsAreSaidWithTheProposal() {
        Mockito.when(facts.forSkill(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(SkillTestFacts.noTools());

        final String result = run(Map.of("folder", "/Hunts", "markdown", SKILL));

        assertThat(result).contains("Its problems", "unknown_tool", "'run_stroomql'");
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void aTrialRunGivesTheDraftsStepsWithTheValuesAndKeepsTheDraft() {
        final String result = run(Map.of("action", "trial", "markdown", SKILL, "parameters", Map.of("user",
                "jsmith")));

        assertThat(result).contains("Trial run of the draft skill 'Logons by user' (not saved; kept at "
                                    + "/outputs/skills/logons-by-user.md) with user=jsmith, days=7 (default)",
                "Follow these steps now", "UserId = 'jsmith'", "now() - 7d", "draft=/outputs/skills/");
        Mockito.verify(workspace).write(Mockito.eq(CHAT), Mockito.any(), Mockito.eq(SKILL), Mockito.any());
        assertThat(takeUserActions()).as("nothing is offered by a trial").isEmpty();
    }

    @Test
    void aTrialRunNeedsTheRequiredParameters() {
        assertThat(run(Map.of("action", "trial", "markdown", SKILL))).contains("Not run: give parameters for user");
    }

    @Test
    void theTemplatesAreOfferedAndOneGivenToFill() {
        assertThat(run(Map.of("action", "template"))).contains("- user-activity-timeline:",
                "- x-then-y-by-same-entity:", "- who-of-group-did-x:");
        assertThat(run(Map.of("action", "template", "name", "who of a group did x")))
                .contains("Fill each [[...]] placeholder", "[[identity index]]", "# Who of a group did X");
    }

    @Test
    void undoProposesTheReverseOfAChangeAppliedAndSaved() {
        final String before = SkillDoc.parse("").withSkill(SKILL, entry(1));
        final String after = SkillDoc.parse(before).withSkill(SKILL.replace("Counts a user's logons.",
                "Counts logons."), entry(2));
        appliedInChat(before, after);
        withSkillsDoc(after);

        final String result = run(Map.of("action", "undo"));

        assertThat(result).contains("undoing version 2", "-Counts logons.", "+Counts a user's logons.",
                "+    change: \"Undid version 2\"");
        final SkillDoc proposed = SkillDoc.parse((String) takeUserActions().getFirst().get("request"));
        assertThat(proposed.body()).isEqualTo(SkillDoc.parse(before).body());
        assertThat(proposed.history()).extracting(SkillDoc.HistoryEntry::version).containsExactly(1, 2, 3);
    }

    @Test
    void undoOfAChangeNotSavedSaysSo() {
        final String before = SkillDoc.parse("").withSkill(SKILL, entry(1));
        appliedInChat(before, SkillDoc.parse(before).withSkill(SKILL.replace("Counts", "Tallies"), entry(2)));
        withSkillsDoc(before);

        assertThat(run(Map.of("action", "undo"))).contains("applied but not saved");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anEditByHandIsRecordedInTheHistory() {
        withSkillsDoc(SkillDoc.parse("").withSkill(SKILL, entry(1)) + "\nA note added by hand.\n");

        final String result = run(Map.of("action", "record", "folder", "/Hunts", "name", "Logons by user"));

        assertThat(result).contains("recording an edit by hand", "+    how: user", "+    author: \"bob\"");
        final SkillDoc proposed = SkillDoc.parse((String) takeUserActions().getFirst().get("request"));
        assertThat(proposed.isEditedSinceLatestEntry()).isFalse();
    }

    @Test
    void aDraftFromTheChatOffersItsValuesAsParametersInAForm() {
        Mockito.when(aiService.getMessages(CHAT)).thenReturn(List.of(
                message(AiMessageType.TOOL_CALL, JsonUtil.writeValueAsString(new ToolCallRecord("a",
                        "activate_sequences", "{}", null))),
                message(AiMessageType.TOOL_CALL, JsonUtil.writeValueAsString(new ToolCallRecord("b",
                        "run_stroomql", JsonUtil.writeValueAsString(Map.of("query",
                        "from Logons where UserId = 'jsmith' select count()")), null)))));
        answer = new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("name", "Logons by user",
                "when_to_use", "how many logons for jsmith\ncount a user's logons",
                "parameters", List.of("jsmith (user_id)"), "output", "A summary in the chat"));

        final String result = run(Map.of("action", "draft"));

        assertThat(result).contains("A draft, from this chat's calls", "# Logons by user",
                "- \"how many logons for jsmith\"", "- user_id (required): [[what it is]] (e.g. jsmith).",
                "UserId = '{user_id}'", "A summary in the chat.")
                .doesNotContain("activate_sequences");
    }

    private void appliedInChat(final String before, final String after) {
        final UserActionRecord applied = new UserActionRecord("apply-skills-change-1",
                UserActions.APPLY_DOCUMENTATION_CHANGE, "label", null, null, null, SKILLS, after, List.of(), 0L,
                UserActionRecord.Status.DONE, "Applied, not saved", null, new UserActionRecord.CodeChange(null,
                null, before, ProposeCodeChangeTool.sha256(before), null, 1, 1));
        Mockito.when(aiService.getMessages(CHAT)).thenReturn(List.of(message(AiMessageType.TOOL_RESULT,
                JsonUtil.writeValueAsString(new ToolResultRecord("c", ProposeSkillTool.NAME, false, "Shown", null,
                        0, 0, List.of(applied))))));
    }

    private void withSkillsDoc(final String text) {
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of(ExplorerNode.builder()
                .docRef(SKILLS).build()));
        Mockito.when(documentAccess.read(SKILLS, DocumentationDoc.class)).thenReturn(Optional.of(
                DocumentationDoc.builder().uuid(SKILLS.getUuid()).name(SKILLS.getName()).data(text)
                        .updateUser("bob").build()));
    }

    private static SkillDoc.HistoryEntry entry(final int version) {
        return new SkillDoc.HistoryEntry(version, "2026-11-23", "jsmith", SkillDoc.HOW_AGENT, "Logons by user",
                "Change " + version, null);
    }

    private static AiChatMessage message(final AiMessageType type, final String text) {
        return new AiChatMessage(null, CHAT, 0L, type, null, text, 1, null);
    }

    private String run(final Map<String, Object> arguments) {
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(new stroom.ai.impl.workspace.SessionWorkspace(workspace, CHAT))
                .runContext(runContext)
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
                        return answer;
                    }
                })
                .build();
        final ToolResult result = runContext.run(() -> tool.execute(new ToolCall("c1", ProposeSkillTool.NAME,
                JsonUtil.writeValueAsString(arguments)), context));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }

    private List<Map<String, Object>> takeUserActions() {
        final List<Map<String, Object>> taken = List.copyOf(offered);
        offered.clear();
        return taken;
    }
}
