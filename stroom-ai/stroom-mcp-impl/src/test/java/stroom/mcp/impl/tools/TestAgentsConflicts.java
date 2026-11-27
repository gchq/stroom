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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.RequiredImport;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The AGENTS rules the folders of a build's feeds give differently ([AgentsConflicts]): decided by the user
/// (2026-11-25), each asked, the answer kept in the build and used.
class TestAgentsConflicts {

    private static final InstructionDoc ROOT = doc("/", """
            ## Fields
            - host: HostName
            """);
    private static final InstructionDoc SITE_A = doc("/Feeds/Acme/Site-A", """
            ## Fields
            - user: Initiator.Id

            ## Required imports
            - translation: Stream-Meta-V1

            ## Documentation
            - field mapping: compact
            """);
    private static final InstructionDoc SITE_B = doc("/Feeds/Acme/Site-B", """
            ## Fields
            - user: user.name

            ## Required imports
            - translation: Stream-Meta-V1, Lookups-V2
            """);

    @Test
    void eachRuleTheFeedsFoldersGiveDifferentlyIsAConflict() {
        final Map<String, List<InstructionDoc>> byFeed = new LinkedHashMap<>();
        byFeed.put("ACME-A", List.of(ROOT, SITE_A));
        byFeed.put("ACME-B", List.of(ROOT, SITE_B));

        final List<AgentsConflicts.Conflict> conflicts = AgentsConflicts.conflicts(byFeed);

        // The root's host line applies to both alike; the layout only one feed's folder gives is taken as it is.
        assertThat(conflicts).extracting(AgentsConflicts.Conflict::key)
                .containsExactly("fields: user", "required imports: translation");
        final AgentsConflicts.Conflict user = conflicts.getFirst();
        assertThat(user.options(List.of("ACME-A", "ACME-B")).keySet()).containsExactly(
                "Initiator.Id (the AGENTS doc in /Feeds/Acme/Site-A, for ACME-A)",
                "user.name (the AGENTS doc in /Feeds/Acme/Site-B, for ACME-B)");
        assertThat(user.question()).doesNotContain("#", "*", "`").contains("fields: user");
    }

    @Test
    void feedsInOneFolderHaveNoConflict() {
        final Map<String, List<InstructionDoc>> byFeed = new LinkedHashMap<>();
        byFeed.put("ACME-A", List.of(ROOT, SITE_A));
        byFeed.put("ACME-A2", List.of(ROOT, SITE_A));

        assertThat(AgentsConflicts.conflicts(byFeed)).isEmpty();
        assertThat(AgentsConflicts.conflicts(Map.of())).isEmpty();
    }

    @Test
    void layoutsThatDifferConflict() {
        final InstructionDoc byType = doc("/Feeds/Acme/Site-B", """
                ## Documentation
                - field mapping: by event type
                """);
        final Map<String, List<InstructionDoc>> byFeed = new LinkedHashMap<>();
        byFeed.put("ACME-A", List.of(SITE_A));
        byFeed.put("ACME-B", List.of(byType));

        assertThat(AgentsConflicts.conflicts(byFeed)).extracting(AgentsConflicts.Conflict::key)
                .contains(AgentsConflicts.DOCUMENTATION);
    }

    @Test
    void theChoiceKeptIsWhatTheToolsApply() {
        final Map<String, Map<String, Object>> chosen = Map.of(
                "required imports: translation", Map.of("value", "Stream-Meta-V1, Lookups-V2", "folder",
                        "/Feeds/Acme/Site-B"),
                "fields: user", Map.of("value", "user.name", "folder", "/Feeds/Acme/Site-B"),
                AgentsConflicts.DOCUMENTATION, Map.of("value", "compact", "folder", "/Feeds/Acme/Site-A"));
        final List<RequiredImport> merged = List.of(new RequiredImport("Stream-Meta-V1", "/Feeds/Acme/Site-A"));

        assertThat(AgentsConflicts.importsChosen(chosen, InstructionRules.TRANSLATION, merged)).containsExactly(
                new RequiredImport("Stream-Meta-V1", "/Feeds/Acme/Site-B"),
                new RequiredImport("Lookups-V2", "/Feeds/Acme/Site-B"));
        // A stage with no conflict keeps the merged imports.
        assertThat(AgentsConflicts.importsChosen(chosen, InstructionRules.INDEXING, merged)).isEqualTo(merged);
        assertThat(AgentsConflicts.importsChosen(Map.of(), InstructionRules.TRANSLATION, merged)).isEqualTo(merged);
        assertThat(AgentsConflicts.fieldsChosen(chosen).get("user").fields()).containsExactly("user.name");
        assertThat(AgentsConflicts.layoutChosen(chosen).orElseThrow().layout()).isEqualTo("compact");
        assertThat(AgentsConflicts.layoutChosen(Map.of())).isEmpty();
    }

    @Test
    void componentRulesThatDifferAreAConflictAndTheChoiceIsReadBack() {
        final InstructionDoc siteA = doc("/Feeds/Acme/Site-A", """
                ## Component templates
                - include: org-base, logs-ilm-90d
                - exclude: legacy-base
                """);
        final InstructionDoc siteB = doc("/Feeds/Acme/Site-B", """
                ## Component templates
                - include: org-base
                """);
        final Map<String, List<InstructionDoc>> byFeed = new LinkedHashMap<>();
        byFeed.put("ACME-A", List.of(siteA));
        byFeed.put("ACME-B", List.of(siteB));

        final AgentsConflicts.Conflict conflict = AgentsConflicts.conflicts(byFeed).getFirst();

        assertThat(conflict.key()).isEqualTo(AgentsConflicts.COMPONENTS);
        final AgentsConflicts.Ruled a = conflict.byFeed().get("ACME-A");
        assertThat(a.value()).isEqualTo("include logs-ilm-90d, org-base; exclude legacy-base");
        final InstructionRules.ComponentRules chosen = AgentsConflicts.componentsChosen(Map.of(
                AgentsConflicts.COMPONENTS, Map.of("value", a.value(), "folder", a.folder()))).orElseThrow();
        assertThat(chosen.include()).extracting(InstructionRules.ComponentRule::name)
                .containsExactly("logs-ilm-90d", "org-base");
        assertThat(chosen.exclude()).extracting(InstructionRules.ComponentRule::name).containsExactly("legacy-base");
        assertThat(chosen.include().getFirst().from()).isEqualTo("/Feeds/Acme/Site-A");
        assertThat(AgentsConflicts.componentsChosen(Map.of())).isEmpty();
    }

    @Test
    void theBuildsFieldChoicesAreThoseOfTheBuildTheDataSourceIsIn() {
        final BuildWorld world = new BuildWorld();
        final DocRef index = world.inBuild("acme", "Index", "ACME-A-Index", "ix");
        world.records().rememberFeedComparison("acme", Map.of("agents", Map.of("fields: user", Map.of("value",
                "account", "folder", "/Feeds/Acme/Site-B"))));
        final BuildFieldRuleChoices choices = new BuildFieldRuleChoices(world::guard, world::records);

        assertThat(choices.chosenFor(index).get("user").fields()).containsExactly("account");
        assertThat(choices.chosenFor(new DocRef("Index", "elsewhere", "Prod-Index"))).isEmpty();
    }

    private static InstructionDoc doc(final String folder, final String text) {
        return new InstructionDoc(new DocRef(DocumentationDoc.TYPE, folder, "AGENTS"), folder, text.strip(),
                List.of());
    }
}
