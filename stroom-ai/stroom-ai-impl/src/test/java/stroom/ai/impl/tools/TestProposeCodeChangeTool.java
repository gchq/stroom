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
import stroom.ai.impl.UserActionLookup;
import stroom.ai.impl.UserActionRecord;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// propose_code_change: offers a change to the selected stepping element's code as a button, after checking the
/// element is in the latest stepping snapshot and selected, the user can view its document, and the proposal is
/// well formed XML that differs from the code. It logs the proposal as a View of the document.
class TestProposeCodeChangeTool {

    private static final int CHAT = 3;
    private static final DocRef PIPELINE = new DocRef("Pipeline", "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90",
            "Acme-Events");
    private static final DocRef XSLT = new DocRef("XSLT", "6b1d3c2e-8f4a-4b9c-a1d2-3e4f5a6b7c8d",
            "Event translation");
    private static final DocRef WRITER = new DocRef("TextWriter", "11111111-2222-3333-4444-555555555555", "W");
    private static final String BASE = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
              <xsl:template match="User">
                <Name><xsl:value-of select="."/></Name>
              </xsl:template>
            </xsl:stylesheet>""";
    private static final String PROPOSED = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
              <xsl:template match="User">
                <Name><xsl:value-of select="lower-case(substring-before(., '@'))"/></Name>
              </xsl:template>
            </xsl:stylesheet>""";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private ProposeCodeChangeTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(documentAccess.canView(XSLT)).thenReturn(true);
        tool = new ProposeCodeChangeTool(workspace, tabSnapshots, documentAccess,
                new AgentInstructions(() -> docFinder, documentAccess));
    }

    @Test
    void aChangeIsOfferedAsAButtonWithItsDiffAndLogged() {
        stepping("xsltFilter", BASE, false);

        final String result = run("xsltFilter", PROPOSED, "Keeps the user name, lower-cased.");

        assertThat(result)
                .contains("shown the change to XSLT 'Event translation' in element 'xsltFilter' (+1 −1 lines")
                .contains("Nothing changes until they apply it")
                .contains("-    <Name><xsl:value-of select=\".\"/></Name>")
                .contains("+    <Name><xsl:value-of select=\"lower-case(substring-before(., '@'))\"/></Name>");
        final List<Map<String, Object>> actions = context.takeUserActions("c1");
        assertThat(actions).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_CODE_CHANGE)
                .containsEntry("element", "xsltFilter")
                .containsEntry("request", PROPOSED)
                .containsEntry("base", BASE)
                .containsEntry("base_hash", ProposeCodeChangeTool.sha256(BASE))
                .containsEntry("snapshot_hash", "snap-hash")
                .containsEntry("added", 1)
                .containsEntry("removed", 1)
                .containsEntry("summary", "Keeps the user name, lower-cased."));
        Mockito.verify(documentAccess).logView(Mockito.eq(CHAT), Mockito.eq(XSLT),
                Mockito.contains("Proposed a change"));

        // Stroom's own check of the action keeps it, with the code it was proposed against.
        final UserActionLookup lookup = Mockito.mock(UserActionLookup.class);
        Mockito.when(lookup.find(new DocRef(XSLT.getType(), XSLT.getUuid()))).thenReturn(Optional.of(XSLT));
        Mockito.when(lookup.find(new DocRef(PIPELINE.getType(), PIPELINE.getUuid())))
                .thenReturn(Optional.of(PIPELINE));
        final List<UserActionRecord> checked = new UserActions(lookup).check(null, actions, 0L);
        assertThat(checked).singleElement().satisfies(action -> {
            assertThat(action.doc()).isEqualTo(XSLT);
            assertThat(action.codeChange().baseCode()).isEqualTo(BASE);
            assertThat(action.codeChange().pipeline()).isEqualTo(PIPELINE);
            assertThat(action.codeChange().snapshotHash()).isEqualTo("snap-hash");
        });
    }

    @Test
    void theAgentsDocsWhereTheCodeIsAreGivenToCheckTheChangeAgainst() {
        final DocRef agents = new DocRef(DocumentationDoc.TYPE, "agents-uuid", AgentInstructions.DOC_NAME);
        Mockito.when(docFinder.findByName(DocumentationDoc.TYPE, AgentInstructions.DOC_NAME, true))
                .thenReturn(List.of(agents));
        Mockito.when(documentAccess.read(agents, DocumentationDoc.class)).thenReturn(Optional.of(
                new DocumentationDoc("agents-uuid", "AGENTS", null, null, null, null, null, null,
                        "## XSLT style\n- Variables in camelCase.")));
        Mockito.when(documentAccess.path(agents)).thenReturn("/Feeds");
        Mockito.when(documentAccess.path(XSLT)).thenReturn("/Feeds/Acme");
        stepping("xsltFilter", BASE, false);

        assertThat(run("xsltFilter", PROPOSED, "Keeps the user name, lower-cased."))
                .contains(AgentInstructions.FOLLOW)
                .contains("- Variables in camelCase.")
                .contains("Check the change follows these.");
    }

    @Test
    void codeThatIsNotWellFormedXmlIsRefusedWithTheParsersError() {
        stepping("xsltFilter", BASE, false);

        assertThat(run("xsltFilter", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                                     + "  <Name>\n</xsl:stylesheet>", "Broken."))
                .contains("isn't well formed XML")
                .contains("line 3");
        assertThat(context.takeUserActions("c1")).isEmpty();
        Mockito.verify(documentAccess, Mockito.never()).logView(Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void aDoctypeIsRefusedAndNothingOutsideTheCodeIsRead() {
        stepping("xsltFilter", BASE, false);
        final String withEntity = "<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";

        assertThat(run("xsltFilter", withEntity, "Reads a file.")).contains("isn't well formed XML")
                .containsIgnoringCase("DOCTYPE");
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void aDroppedOrAddedFinalLineBreakIsNotAChange() {
        // The code ends with a line break, as an editor's usually does, and the model's doesn't.
        stepping("xsltFilter", BASE + "\n", false);

        assertThat(run("xsltFilter", PROPOSED, "Keeps the user name, lower-cased."))
                .contains("(+1 −1 lines, in 1 place(s))");
        assertThat(context.takeUserActions("c1")).singleElement().satisfies(action ->
                assertThat(action).containsEntry("request", PROPOSED + "\n"));

        assertThat(ProposeCodeChangeTool.withLineEndingOf("a\r\n", "b")).isEqualTo("b\r\n");
        assertThat(ProposeCodeChangeTool.withLineEndingOf("a", "b\n\n")).isEqualTo("b");
        assertThat(ProposeCodeChangeTool.withLineEndingOf("a\n", "b\n")).isEqualTo("b\n");
    }

    @Test
    void aProposalTheSameAsTheCodeIsRefused() {
        stepping("xsltFilter", BASE, false);

        // Line endings aside, it is the same.
        assertThat(run("xsltFilter", BASE.replace("\n", "\r\n"), "Nothing."))
                .contains("the same as the element's current code");
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void anElementTheUserCantViewIsTreatedAsNoPermission() {
        stepping("xsltFilter", BASE, false);
        Mockito.when(documentAccess.canView(XSLT)).thenReturn(false);

        assertThat(run("xsltFilter", PROPOSED, "Change.")).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(context.takeUserActions("c1")).isEmpty();
        Mockito.verify(documentAccess, Mockito.never()).logView(Mockito.anyInt(), Mockito.any(), Mockito.any());
    }

    @Test
    void anUnknownOrUnselectedElementIsRefused() {
        stepping("xsltFilter", BASE, false);

        assertThat(run("missing", PROPOSED, "Change.")).contains("has no element 'missing'")
                .contains("xsltFilter, writer");
        assertThat(run("writer", PROPOSED, "Change.")).contains("isn't selected")
                .contains("Ask the user to select it");
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void withoutASteppingTabNothingIsProposed() {
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());

        assertThat(run("xsltFilter", PROPOSED, "Change.")).isEqualTo(ProposeCodeChangeTool.NOT_STEPPING);
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void codeCutShortForSizeCantBeChanged() {
        stepping("xsltFilter", BASE, true);

        assertThat(run("xsltFilter", PROPOSED, "Change.")).contains("too large to have been shared in full");
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void onlyXsltsAndTextConvertersCanBeChanged() {
        final AiSteppingContext stepping = new AiSteppingContext("ACME", 12L, "Raw Events", null, 1L, 1L,
                "writer", List.of(new AiSteppingElement("writer", "TextWriter", WRITER, false, false, 0, 0,
                List.of())), List.of());
        Mockito.when(documentAccess.canView(WRITER)).thenReturn(true);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot(stepping)));

        assertThat(run("writer", PROPOSED, "Change.")).contains("Only an XSLT's or a text converter's code");
    }

    private void stepping(final String selected, final String code, final boolean cut) {
        final AiSteppingContext stepping = new AiSteppingContext("ACME", 12L, "Raw Events", null, 1L, 5L, selected,
                List.of(new AiSteppingElement("xsltFilter", "XSLTFilter", XSLT, true, false, 1, 0, List.of()),
                        new AiSteppingElement("writer", "TextWriter", WRITER, false, false, 0, 0, null)),
                List.of());
        final StoredTabSnapshot snapshot = snapshot(stepping);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.STEP_CODE))
                .thenReturn(Optional.of(new UnsavedPart(code, cut, "/tabs/1/code.xsl")));
    }

    private static StoredTabSnapshot snapshot(final AiSteppingContext stepping) {
        return new StoredTabSnapshot(5, 1, PIPELINE, "/", "Acme-Events", "Stepping", false, null, "snap-hash", 0L,
                List.of(new StoredPart(AiTabSnapshotPart.STEP_CODE, "application/xslt+xml", "/tabs/1/code.xsl",
                        10, 10, false)), List.of(), stepping);
    }

    private String run(final String element, final String code, final String summary) {
        final String arguments = JsonUtil.writeValueAsString(Map.of("element", element, "code", code,
                "summary", summary));
        return context.run(() -> tool.execute(ToolExecutionRequest.builder().id("c1").name(ProposeCodeChangeTool.NAME)
                .arguments(arguments).build(), CHAT));
    }
}
