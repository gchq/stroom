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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.InstructionRules.ComponentRule;
import stroom.ai.impl.tools.instructions.InstructionRules.ComponentRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldMappingLayout;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.instructions.InstructionRules.RequiredImport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The sections of AGENTS docs the tools apply themselves.
class TestInstructionRules {

    private static final String AGENTS = """
            # Standing instructions

            ## Fields
            - Host names are lower case.

            ## Required imports
            - Raw Events to Events: `Stream-Meta-V1`
            - indexing: Common-Index-V1, Lookups-V2
            - something else: Ignored-V1

            ## Naming
            - translation: not an import
            """;

    @Test
    void theRequiredImportsSectionIsReadPerStage() {
        // Asked for by the user: every Raw Events to Events translation imports a Stream-Meta-V1 translation.
        final Map<String, List<RequiredImport>> found = InstructionRules.requiredImports(List.of(
                doc("/", AGENTS),
                doc("/Feeds/Acme", "## Required imports\n- translation: Stream-Meta-V1, Acme-V1")));

        assertThat(found).containsOnlyKeys(InstructionRules.TRANSLATION, InstructionRules.INDEXING);
        assertThat(found.get(InstructionRules.TRANSLATION)).containsExactly(
                new RequiredImport("Stream-Meta-V1", "/"),
                new RequiredImport("Acme-V1", "/Feeds/Acme"));
        assertThat(found.get(InstructionRules.INDEXING)).containsExactly(
                new RequiredImport("Common-Index-V1", "/"),
                new RequiredImport("Lookups-V2", "/"));
        assertThat(InstructionRules.requiredImports(List.of(doc("/", "## Fields\n- translation: X")))).isEmpty();
    }

    @Test
    void agentsDocsSayWhichComponentTemplatesToIncludeOrExclude() {
        // Asked for by the user: standing instructions about component templates are followed, the more specific
        // last.
        final ComponentRules rules = InstructionRules.componentRules(List.of(
                doc("/", "## Component templates\n- include: org-base, ilm-90d\n- exclude: legacy\n"),
                doc("/Feeds/Acme", "## Component templates\n- never: ilm-90d\n- note: not a rule\n")));

        assertThat(rules.include()).extracting(ComponentRule::name).containsExactly("org-base");
        assertThat(rules.exclude()).extracting(ComponentRule::name).containsExactlyInAnyOrder("ilm-90d", "legacy");
        assertThat(rules.exclude()).contains(new ComponentRule("ilm-90d", "/Feeds/Acme"));
    }

    @Test
    void agentsDocsSayHowTheFieldMappingIsLaidOut() {
        // Asked for by the user: by event type by default, configurable in an AGENTS doc; the more specific doc
        // wins.
        assertThat(InstructionRules.fieldMappingLayout(List.of(doc("/", "## Component templates\n- include: x\n"))))
                .isEmpty();
        assertThat(InstructionRules.fieldMappingLayout(List.of(
                doc("/", "## Documentation\n- field mapping: compact\n"),
                doc("/Feeds/Acme", "# Notes\n\n## Documentation\n- Field mapping: by event type\n"))))
                .contains(new FieldMappingLayout(InstructionRules.BY_EVENT_TYPE, "/Feeds/Acme"));
        assertThat(InstructionRules.fieldMappingLayout(List.of(
                doc("", "## Documentation\n- field mapping: one table"))))
                .hasValueSatisfying(layout -> assertThat(layout.layout()).isEqualTo(InstructionRules.COMPACT));
    }

    @Test
    void linesOfTheAppliedSectionsThatNameNothingKnownArePointedOut() {
        assertThat(InstructionRules.problems("""
                # Standing instructions
                - anything: goes outside the sections
                ## Required imports
                - translation: Stream-Meta-V1
                - indexng: Common-Index-V1
                ## Component templates
                - include: org-base
                - sometimes: legacy
                ## Documentation
                - field mapping: sideways
                ## Naming
                - feeds: UPPER-CASE
                """)).containsExactly(
                "'## Required imports' line '- indexng: Common-Index-V1' names none the tools know (cef, events, "
                + "forwarding, indexing, raw event to event, raw event to record, raw events to events, raw events "
                + "to records, records, reference, reference data, translation, translations), so it would be left "
                + "out",
                "'## Component templates' line '- sometimes: legacy' names none the tools know (add, always, drop, "
                + "exclude, include, never, omit, use), so it would be left out",
                "'## Documentation' line '- field mapping: sideways' names no layout the tools know (by event type, "
                + "by_event_type, compact, one table, per event type, single table)");
        assertThat(InstructionRules.problems("## Required imports\n- `Translation`: Stream-Meta-V1\n")).isEmpty();
    }

    @Test
    void theFieldsSectionNamesEachThingsFieldsTheMostSpecificDocWinning() {
        // A site standard (the root's), and an index folder that writes its user differently.
        final Map<String, FieldRule> fields = InstructionRules.fields(List.of(
                doc("/", "## Fields\n- user: Initiator.Id, user.name\n- host: HostName\n- Host names are lower "
                         + "case.\n"),
                doc("/Indexes/Proxy", "## Fields\n- `User`: SrcUser\n- event type: `Vendor.EventId`\n")));

        assertThat(fields).containsOnlyKeys(InstructionRules.USER_FIELD, InstructionRules.HOST_FIELD,
                InstructionRules.EVENT_TYPE_FIELD);
        assertThat(fields.get(InstructionRules.USER_FIELD)).isEqualTo(new FieldRule(List.of("SrcUser"),
                "/Indexes/Proxy"));
        assertThat(fields.get(InstructionRules.HOST_FIELD)).isEqualTo(new FieldRule(List.of("HostName"), "/"));
        assertThat(fields.get(InstructionRules.EVENT_TYPE_FIELD).fields()).containsExactly("Vendor.EventId");
    }

    @Test
    void aFieldsLineNamingNothingKnownOrNoFieldTheIndexesHaveIsPointedOut() {
        final String text = "## Fields\n- user: Initiator.ID\n- host: HostName, host.name\n- colour: Red\n";

        assertThat(InstructionRules.problems(text)).singleElement().asString()
                .startsWith("'## Fields' line '- colour: Red' names none the tools know (action, application, "
                            + "element, event code, event detail, event type, host, object, process, user)");
        assertThat(InstructionRules.fieldProblems(text, Set.of("Initiator.Id", "HostName")))
                .containsExactly("'## Fields' line for user names no field the data sources where the doc applies "
                                 + "have (Initiator.ID)");
        // No fields known: nothing is said.
        assertThat(InstructionRules.fieldProblems(text, Set.of())).isEmpty();
    }

    private static InstructionDoc doc(final String folder, final String text) {
        return new InstructionDoc(null, folder, text, List.of());
    }
}
