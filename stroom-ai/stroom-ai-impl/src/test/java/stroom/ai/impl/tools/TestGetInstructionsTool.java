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

import stroom.ai.impl.tools.AgentInstructions.Targets;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// `get_instructions`, as the Stroom MCP server's tests of its tool of the same name have it: a doc applies to its
/// folder and below, one at the top everywhere, most general first.
class TestGetInstructionsTool {

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        fixture.addInstructions("g", "Always map the acting user to EventSource/User/Id.", true);
        fixture.addInstructions("e", "Event feeds: TypeId is the vendor event code.", true, "Feeds", "Events");
        fixture.addInstructions("k", "Keycloak: realm goes in EventSource/System/Environment.", true, "Feeds",
                "Events", "Keycloak");
        fixture.addInstructions("x", "Index names are ecs-<source>-v<n>.", true, "Elastic Indices");
        fixture.folders.put("f", List.of("Feeds", "Events", "Keycloak"));
        fixture.add(new DocRef(FeedDoc.TYPE, "f", "KEYCLOAK-V1.2"), null, true);
    }

    @Test
    void theScopeIsTheFolderAndBelowAndRootDocsApplyEverywhere() {
        assertThat(new AgentInstructions.InstructionDoc(null, "/", "", List.of()).appliesTo("/Anything")).isTrue();
        assertThat(new AgentInstructions.InstructionDoc(null, "/Feeds/Events", "", List.of())
                .appliesTo("/Feeds/Events/Keycloak")).isTrue();
        assertThat(new AgentInstructions.InstructionDoc(null, "/Elastic Indices", "", List.of())
                .appliesTo("/Feeds/Events")).isFalse();
    }

    @Test
    void instructionsForAFeedRunFromGeneralToSpecific() {
        final ToolResult result = run("{\"feeds\":[\"KEYCLOAK-V1.2\"]}");

        final List<Map<String, Object>> instructions = instructions(result);
        assertThat(instructions).extracting(entry -> entry.get("folder"))
                .containsExactly("/", "/Feeds/Events", "/Feeds/Events/Keycloak");
        assertThat(instructions.getFirst()).containsEntry("applies_to", "everything");
        assertThat(instructions.get(1)).containsEntry("applies_to", "/Feeds/Events and below");
        assertThat((String) instructions.get(2).get("instructions")).startsWith("Keycloak: realm");
        assertThat(result.structuredContent())
                .containsEntry("doc_name", "AGENTS")
                .containsEntry("other_instruction_docs", List.of(Map.of("folder", "/Elastic Indices", "uuid", "x")))
                .containsEntry("hint", AgentInstructions.HINT_APPLYING);
        // The text gives them too, the more specific last.
        assertThat(result.text())
                .startsWith("3 AGENTS doc(s) apply.")
                .contains("Other AGENTS docs, which don't apply there: in /Elastic Indices.");
        assertThat(result.text().indexOf("Event feeds:")).isLessThan(result.text().indexOf("Keycloak: realm"));
    }

    @Test
    void withoutTargetsOnlyRootDocsApplyAndNamesMustMatchExactly() {
        // A search for the name finds other docs whose names contain it.
        final DocRef notes = fixture.add(new DocRef(DocumentationDoc.TYPE, "n", "AGENTS notes"),
                new DocumentationDoc("n", "AGENTS notes", null, null, null, null, null, null, "Not instructions."),
                true);
        final List<DocRef> found = fixture.docRefs.values().stream()
                .filter(docRef -> DocumentationDoc.TYPE.equals(docRef.getType()))
                .toList();
        Mockito.when(fixture.docFinder.findByName(DocumentationDoc.TYPE, AgentInstructions.DOC_NAME, true))
                .thenReturn(found);

        final ToolResult result = run("{}");

        assertThat(instructions(result)).extracting(entry -> entry.get("uuid")).containsExactly("g");
        assertThat(others(result)).extracting(entry -> entry.get("uuid"))
                .containsExactlyInAnyOrder("e", "k", "x")
                .doesNotContain(notes.getUuid());
    }

    @Test
    void foldersCanBeGivenDirectly() {
        // By their path, as Stroom's explorer search gives it, and whether or not they exist yet (e.g. where the
        // work will be promoted to).
        final ToolResult result = run("{\"folders\":[\"System/Elastic Indices/Keycloak\"]}");

        assertThat(instructions(result)).extracting(entry -> entry.get("uuid")).containsExactly("g", "x");
        assertThat(result.text()).contains("Not found, or you don't have permission to view them: System/Elastic "
                                           + "Indices/Keycloak.");
    }

    @Test
    void documentsCanBeGivenByTypeAndName() {
        final ToolResult byName = run("{\"docs\":[{\"type\":\"Feed\",\"name\":\"KEYCLOAK-V1.2\"}]}");
        final ToolResult byUuid = run("{\"docs\":[{\"type\":\"Feed\",\"name\":\"KEYCLOAK-V1.2\",\"uuid\":\"f\"}]}");
        final ToolResult missing = run("{\"docs\":[{\"type\":\"Pipeline\",\"name\":\"Nope\"}]}");

        assertThat(instructions(byName)).extracting(entry -> entry.get("uuid")).containsExactly("g", "e", "k");
        assertThat(instructions(byUuid)).extracting(entry -> entry.get("uuid")).containsExactly("g", "e", "k");
        assertThat(missing.text()).contains("Not found, or you don't have permission to view them: Pipeline "
                                            + "'Nope'.");
    }

    @Test
    void aDocThatCantBeReadIsSaidAndTheOthersStillFollowed() {
        // Seen with the Stroom MCP server: one such doc had failed every tool that hands back standing
        // instructions.
        fixture.folders.put("gone", List.of("Feeds"));
        fixture.add(new DocRef(DocumentationDoc.TYPE, "gone", AgentInstructions.DOC_NAME), null, true);

        final ToolResult result = run("{\"feeds\":[\"KEYCLOAK-V1.2\"]}");

        assertThat(instructions(result)).hasSize(3);
        assertThat(result.structuredContent()).containsEntry("unreadable_instruction_docs", List.of(Map.of(
                "folder", "/Feeds", "uuid", "gone", "reason", AgentInstructions.UNREADABLE)));
        assertThat(result.text()).contains("the doc in /Feeds (uuid gone): it couldn't be read");
    }

    @Test
    void whenNoneApplyTheHintSaysHowToAddOne() {
        final ToolTestFixture empty = new ToolTestFixture(tempDir);
        final ToolResult result = new GetInstructionsTool(empty.agentInstructions(), Optional.empty())
                .execute(ToolTestFixture.request(GetInstructionsTool.NAME, "{}"), ToolCalls.inChat(1,
                        empty.workspace));

        assertThat(result.structuredContent()).containsEntry("hint", "No AGENTS docs apply. Users can add one: a "
                                                                     + "Documentation doc named AGENTS in a folder "
                                                                     + "applies to that folder and below.");
        assertThat(result.structuredContent()).doesNotContainKey("unreadable_instruction_docs");
    }

    @Test
    void theRulesOfTheDocsThatApplyCanBeAskedForByOtherTools() {
        fixture.addInstructions("r", "## Required imports\n- translation: Stream-Meta-V1\n", true, "Feeds");
        final AgentInstructions agentInstructions = fixture.agentInstructions();

        assertThat(agentInstructions.importsRequired(ToolSession.chat(1), InstructionRules.TRANSLATION,
                List.of(), List.of("KEYCLOAK-V1.2")))
                .containsExactly(new InstructionRules.RequiredImport("Stream-Meta-V1", "/Feeds"));
        assertThat(agentInstructions.importsRequired(ToolSession.chat(1), InstructionRules.TRANSLATION,
                List.of("/Elastic Indices"), List.of()))
                .isEmpty();
        final Targets targets = agentInstructions.targets(List.of(), List.of("f"), List.of(), List.of());
        assertThat(targets.paths()).containsExactly("/", "/Feeds/Events/Keycloak");
    }

    @Test
    void aGuideIsReadByName() {
        final AgentKnowledge knowledge = new AgentKnowledge() {
            @Override
            public List<String> guideNames() {
                return List.of("cef", "xslt");
            }

            @Override
            public Optional<String> guide(final String name) {
                return "xslt".equals(name)
                        ? Optional.of("# Writing XSLT\n\nUse templates.")
                        : Optional.empty();
            }

            @Override
            public List<String> conventionNames() {
                return List.of();
            }

            @Override
            public Optional<String> convention(final String name) {
                return Optional.empty();
            }
        };
        final GetInstructionsTool tool = new GetInstructionsTool(fixture.agentInstructions(), Optional.of(knowledge));

        assertThat(run(tool, "{\"guide\":\"xslt\"}").text()).isEqualTo("# Writing XSLT\n\nUse templates.");
        assertThat(run(tool, "{\"guide\":\"guides\"}").text())
                .startsWith("# Guides\n\n- cef: \n- xslt: Writing XSLT");
        assertThat(run(tool, "{\"guide\":\"nope\"}").text()).isEqualTo("Error: No guide 'nope'. Guides: cef, xslt");
        assertThat(run("{\"guide\":\"xslt\"}").text()).startsWith("Error: no working guides are bundled");
    }

    private ToolResult run(final String arguments) {
        return run(new GetInstructionsTool(fixture.agentInstructions(), Optional.empty()), arguments);
    }

    private ToolResult run(final GetInstructionsTool tool, final String arguments) {
        return tool.execute(ToolTestFixture.request(GetInstructionsTool.NAME, arguments), ToolCalls.inChat(1,
                fixture.workspace));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> instructions(final ToolResult result) {
        return (List<Map<String, Object>>) result.structuredContent().get("instructions");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> others(final ToolResult result) {
        return (List<Map<String, Object>>) result.structuredContent().get("other_instruction_docs");
    }
}
