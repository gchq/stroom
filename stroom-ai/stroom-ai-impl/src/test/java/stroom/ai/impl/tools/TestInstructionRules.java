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
import stroom.ai.impl.tools.InstructionRules.ComponentRule;
import stroom.ai.impl.tools.InstructionRules.ComponentRules;
import stroom.ai.impl.tools.InstructionRules.FieldMappingLayout;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    private static InstructionDoc doc(final String folder, final String text) {
        return new InstructionDoc(null, folder, text, List.of());
    }
}
