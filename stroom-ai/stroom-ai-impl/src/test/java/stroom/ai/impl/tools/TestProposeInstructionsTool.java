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

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private ProposeInstructionsTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(documentAccess.findFolder("/Feeds/Acme")).thenReturn(Optional.of(FOLDER));
        Mockito.when(documentAccess.path(FOLDER)).thenReturn("/Feeds/Acme");
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
    void aTypedSectionLineTheToolsWouldLeaveOutIsPointedOut() {
        final String result = run(Map.of("folder", "/Feeds/Acme", "markdown",
                "## Required imports\n\n- translations: Stream-Meta-V1\n- indexng: Common-Index-V1\n"));

        assertThat(result).contains("The tools will leave these lines out",
                        "'- indexng: Common-Index-V1' names none the tools know", "propose the corrected text again")
                .doesNotContain("translations: Stream-Meta-V1' names");
        // Still offered: it may be a note.
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void theSameTextIsNothingToPropose() {
        withAgentsDoc(SAVED);

        assertThat(run(Map.of("folder", "/Feeds/Acme", "markdown", SAVED))).contains("nothing to propose");
        assertThat(takeUserActions()).isEmpty();
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
