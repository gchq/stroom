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

import stroom.ai.impl.tools.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.AgentInstructions.Standing;

import event.logging.ViewEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestAgentInstructions {

    private static final String TOP = """
            # Standing instructions
            ## Ignore
            - folders: Archive
            """;
    private static final String FEEDS = """
            ## Fields
            - Use Initiator.Id for the actioning user when searching an index.

            ## Ignore
            - folders: `Old`, Events/Retired, /Elsewhere/Outside
            - reason: superseded

            ## XSLT style
            - Variables in camelCase.
            """;

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        fixture.addInstructions("feeds-agents", FEEDS, true, "Feeds");
        fixture.addInstructions("top-agents", TOP, true);
        fixture.addInstructions("secret-agents", "## Ignore\n- folders: Feeds\n", false, "Secret");
    }

    @Test
    void docsApplyToTheirFolderAndBelowMostGeneralFirst() {
        final Standing standing = load();

        assertThat(standing.docs()).extracting(InstructionDoc::folder).containsExactly("/", "/Feeds");
        assertThat(standing.applyingTo(List.of("/Feeds/Events"))).extracting(InstructionDoc::folder)
                .containsExactly("/", "/Feeds");
        // Only the one at the top applies elsewhere, as feeds-agents applies to /Feeds and below.
        assertThat(standing.applyingTo(List.of("/Indexes"))).extracting(InstructionDoc::folder)
                .containsExactly("/");
        assertThat(standing.applyingTo(List.of("/FeedsOther"))).extracting(InstructionDoc::folder)
                .containsExactly("/");
        // Reading them is logged.
        assertThat(fixture.logged(ViewEventAction.class)).singleElement()
                .satisfies(view -> assertThat(view.toString()).doesNotContain("secret-agents"));
    }

    @Test
    void aDocTheUserCantViewIsLeftOut() {
        // secret-agents would ignore /Feeds, but the user can't view it.
        assertThat(load().docs()).extracting(doc -> doc.docRef().getUuid())
                .containsExactly("top-agents", "feeds-agents");
        assertThat(load().ignored("/Feeds/Events")).isEmpty();
    }

    @Test
    void ignoredFoldersAreResolvedFromTheDocsFolderAndKeptWithinItsScope() {
        assertThat(AgentInstructions.ignoredFolders("/Feeds", FEEDS))
                // Outside /Feeds, so not this doc's to ignore; the reason line names no folders.
                .containsExactly("/Feeds/Old", "/Feeds/Events/Retired");
        assertThat(AgentInstructions.ignoredFolders("/", "### Ignored folders\n* folder: System/Archive\n"))
                .containsExactly("/Archive");
        assertThat(AgentInstructions.ignoredFolders("/", "## Fields\n- folders: Archive\n")).isEmpty();
    }

    @Test
    void aPathInAnIgnoredFolderSaysWhichDocIgnoresIt() {
        final Standing standing = load();

        assertThat(standing.ignoresAny()).isTrue();
        assertThat(standing.ignored("/Archive/2020/Old feed")).hasValueSatisfying(ignored ->
                assertThat(ignored.describe()).isEqualTo("/Archive (ignored by the AGENTS doc in /)"));
        assertThat(standing.ignored("/feeds/old")).hasValueSatisfying(ignored ->
                assertThat(ignored.folder()).isEqualTo("/Feeds/Old"));
        assertThat(standing.ignored("/Feeds/Events")).isEmpty();
        assertThat(standing.ignored("/Archived")).isEmpty();
    }

    @Test
    void theNoteGivesTheTextOfThoseThatApplyCutAtTheMost() {
        final String note = load().note(StroomLinks.CHAT, List.of("/Feeds"));

        assertThat(note)
                .contains(AgentInstructions.FOLLOW)
                .contains("in / (applies to everything)")
                .contains("in /Feeds (applies to /Feeds and below)")
                .contains("Use Initiator.Id for the actioning user");
        assertThat(note.indexOf("in / (")).isLessThan(note.indexOf("in /Feeds ("));
        assertThat(load().note(StroomLinks.CHAT, List.of())).isEmpty();

        fixture.addInstructions("long-agents", "x".repeat(AgentInstructions.MAX_CHARS + 10), true, "Long");
        assertThat(load().note(StroomLinks.CHAT, List.of("/Long")))
                .contains("[cut short; read the rest with read_document]");
    }

    @Test
    void getInstructionsGivesThoseThatApplyAndNamesTheOthers() {
        final GetInstructionsTool tool = new GetInstructionsTool(fixture.agentInstructions(), Optional.empty());

        // With nothing given, those that apply everywhere.
        assertThat(tool.execute(ToolTestFixture.request(GetInstructionsTool.NAME, "{}"), ToolCalls.inChat(1,
                fixture.workspace)).text())
                .startsWith("1 AGENTS doc(s) apply.")
                .contains("in / (applies to everything)")
                .doesNotContain("Initiator.Id")
                .contains("Other AGENTS docs, which don't apply there: in /Feeds.");
        assertThat(tool.execute(ToolTestFixture.request(GetInstructionsTool.NAME,
                "{\"uuids\":[\"no-such-uuid\"]}"), ToolCalls.inChat(1, fixture.workspace)).text())
                .contains("Not found, or you don't have permission to view them: no-such-uuid.");
    }

    private Standing load() {
        return fixture.agentInstructions().load(ToolSession.chat(1), "test");
    }
}
