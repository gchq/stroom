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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredPart;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.dashboard.shared.DashboardDoc;
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

class TestProposeDocumentationChangeTool {

    private static final int CHAT = 3;
    private static final DocRef DASHBOARD = new DocRef(DashboardDoc.TYPE, "dashboard-uuid", "Logons");
    private static final String SAVED = "# Logons\n\nOld text.\n";
    private static final String UNSAVED = "# Logons\n\nText the user is writing.\n";
    private static final String PROPOSED = "# Logons\n\nWho logged on, and from where.\n";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ProposeDocumentationChangeTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());
        document(DASHBOARD, DashboardDoc.builder().uuid(DASHBOARD.getUuid()).name(DASHBOARD.getName())
                .description(SAVED).build());
        tool = new ProposeDocumentationChangeTool(workspace, tabSnapshots, documentAccess);
    }

    @Test
    void aChangeToTheSavedDocumentationIsOfferedWithAReviewButton() {
        final String result = run(Map.of("uuid", DASHBOARD.getUuid(), "markdown", PROPOSED.strip()));

        assertThat(result)
                .contains("documentation of Dashboard 'Logons'")
                .contains("+1 −1 lines")
                .contains("'Review and apply' button")
                .contains("against the documentation as saved")
                .contains("-Old text.")
                .contains("+Who logged on, and from where.");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_DOCUMENTATION_CHANGE)
                .containsEntry("doc", Map.of("type", DashboardDoc.TYPE, "uuid", DASHBOARD.getUuid()))
                // Ending as the documentation does.
                .containsEntry("request", PROPOSED)
                .containsEntry("base", SAVED)
                .containsEntry("base_hash", ProposeCodeChangeTool.sha256(SAVED))
                .doesNotContainKey("snapshot_hash")
                .containsEntry("added", 1)
                .containsEntry("removed", 1)
                .containsEntry("summary", "Says who logged on."));
        Mockito.verify(documentAccess).logView(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.contains("documentation"));
    }

    @Test
    void theAttachedTabsUnsavedDocumentationIsTheBase() {
        tab(DASHBOARD, UNSAVED, false);

        final String result = run(Map.of("uuid", DASHBOARD.getUuid(), "edits", List.of(Map.of(
                "oldText", "Text the user is writing.", "newText", "Who logged on, and from where."))));

        assertThat(result).contains("unsaved edits included");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("request", PROPOSED)
                .containsEntry("base", UNSAVED)
                .containsEntry("base_hash", ProposeCodeChangeTool.sha256(UNSAVED))
                .containsEntry("snapshot_hash", "snap-hash"));
    }

    @Test
    void anotherDocumentsTabIsNotTheBase() {
        tab(new DocRef("XSLT", "xslt-uuid", "X"), UNSAVED, false);

        run(Map.of("uuid", DASHBOARD.getUuid(), "markdown", PROPOSED));

        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("base", SAVED)
                .doesNotContainKey("snapshot_hash"));
    }

    @Test
    void aDocumentWithNoDocumentationYetCanBeGivenSome() {
        document(DASHBOARD, DashboardDoc.builder().uuid(DASHBOARD.getUuid()).name(DASHBOARD.getName()).build());

        run(Map.of("uuid", DASHBOARD.getUuid(), "markdown", PROPOSED));

        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("request", PROPOSED.strip())
                .containsEntry("base", "")
                .containsEntry("removed", 0));
    }

    @Test
    void aDocumentTheUserCantViewIsRefusedAsIfItDidNotExist() {
        Mockito.when(documentAccess.find("hidden-uuid")).thenReturn(Optional.empty());

        assertThat(run(Map.of("uuid", "hidden-uuid", "markdown", PROPOSED))).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void foldersDocumentationDocumentsAndTypesWithoutDocumentationAreRefused() {
        final DocRef folder = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Logons");
        document(folder, null);
        final DocRef documentation = new DocRef(DocumentationDoc.TYPE, "documentation-uuid", "Read me");
        document(documentation, DocumentationDoc.builder().uuid(documentation.getUuid()).data("Text").build());
        final DocRef noField = new DocRef("Thing", "thing-uuid", "Thing");
        document(noField, Map.of("name", "Thing"));

        for (final DocRef docRef : List.of(folder, documentation, noField)) {
            assertThat(run(Map.of("uuid", docRef.getUuid(), "markdown", PROPOSED)))
                    .contains(ProposeDocumentationChangeTool.NO_DOCUMENTATION);
        }
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void theSameTextOrATabTooLargeToShareIsRefused() {
        assertThat(run(Map.of("uuid", DASHBOARD.getUuid(), "markdown", SAVED.strip())))
                .contains("nothing to propose");

        tab(DASHBOARD, UNSAVED, true);
        assertThat(run(Map.of("uuid", DASHBOARD.getUuid(), "markdown", PROPOSED))).contains("too large");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void badArgumentsAreRefused() {
        assertThat(run(Map.of("markdown", PROPOSED))).isEqualTo("Error: Give the document's uuid.");
        assertThat(run(Map.of("uuid", DASHBOARD.getUuid())))
                .contains("Give either edits or the whole proposed markdown");
        assertThat(run(Map.of("uuid", DASHBOARD.getUuid(), "markdown", PROPOSED,
                "edits", List.of(Map.of("oldText", "Old", "newText", "New")))))
                .contains("but not both");
        assertThat(run(Map.of("uuid", DASHBOARD.getUuid(), "edits",
                List.of(Map.of("oldText", "Missing", "newText", "New")))))
                .contains("isn't in the documentation");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aDocumentHasDocumentationIfItHasADescriptionField() {
        assertThat(ProposeDocumentationChangeTool.hasDocumentation(DashboardDoc.builder().uuid("d").build()))
                .isTrue();
        assertThat(ProposeDocumentationChangeTool.hasDocumentation(DocumentationDoc.builder().uuid("d").build()))
                .isFalse();
        assertThat(ProposeDocumentationChangeTool.hasDocumentation("text")).isFalse();
    }

    private void document(final DocRef docRef, final Object doc) {
        Mockito.when(documentAccess.find(docRef.getUuid()))
                .thenReturn(Optional.of(ExplorerNode.builder().docRef(docRef).build()));
        Mockito.when(documentAccess.read(docRef)).thenReturn(Optional.ofNullable(doc));
    }

    private void tab(final DocRef docRef, final String documentation, final boolean cut) {
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, docRef, "/", docRef.getName(), null, false,
                null, "snap-hash", 0L, List.of(new StoredPart(AiTabSnapshotPart.DOCUMENTATION, "text/markdown",
                "/tabs/1/documentation.md", 10, 10, cut)), List.of(), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.DOCUMENTATION))
                .thenReturn(Optional.of(new UnsavedPart(documentation, cut, "/tabs/1/documentation.md")));
    }

    private String run(final Map<String, Object> arguments) {
        final Map<String, Object> withSummary = new HashMap<>(arguments);
        withSummary.put("summary", "Says who logged on.");
        final ToolResult result = context.run(() -> tool.execute(
                new ToolCall("c1", ProposeDocumentationChangeTool.NAME, JsonUtil.writeValueAsString(withSummary)),
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
