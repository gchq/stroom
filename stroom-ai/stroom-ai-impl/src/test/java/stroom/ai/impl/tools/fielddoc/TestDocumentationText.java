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

package stroom.ai.impl.tools.fielddoc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// A Documentation doc's title is its name; its sections at `## `; text added under the agent's own. Ported from the
/// Stroom MCP server's `tests/test_documentation_title.py` (seen in production: the title said V1.2 on the doc of
/// the V1.1 pipeline) and `test_the_summary_goes_under_the_agents_own_purpose` in `test_fielddoc.py`.
class TestDocumentationText {

    @Test
    void theAgentsTitleGivesWayToTheDocsName() {
        assertThat(DocumentationText.titled("# ACME-V1.2-Events\n\n## Purpose and data\nA door.", "ACME-V1.1-Events"))
                .isEqualTo("# ACME-V1.1-Events\n\n## Purpose and data\nA door.");
    }

    @Test
    void aDocWithoutATitleGetsOneAndSectionsStay() {
        assertThat(DocumentationText.titled("## Purpose and data\nA door.", "ACME"))
                .isEqualTo("# ACME\n\n## Purpose and data\nA door.");
        // Only the first line, and only a level-one heading: a later '# ...' line (a comment in a code block) stays.
        assertThat(DocumentationText.titled("Intro\n# not a title", "ACME"))
                .isEqualTo("# ACME\n\nIntro\n# not a title");
        assertThat(DocumentationText.titled(null, "ACME")).isEqualTo("# ACME\n\n");
    }

    @Test
    void sectionsWrittenAtLevelOneGoDownALevel() {
        // Seen (Haiku, eval): '# Purpose and data' first; the survey went in a '## Purpose and data' of its own
        // above it, and as a pipeline's first line the heading would have been taken for the title.
        final String given = "# ACME\n\n# Purpose and data\n\nA door.\n\n## Volumes\n\nFew.\n\n```\n# a comment\n```"
                             + "\n\n# Open items\n\nNone.";
        assertThat(DocumentationText.sectionLevels(given)).isEqualTo("## Purpose and data\n\nA door.\n\n### Volumes\n\n"
                                                                     + "Few.\n\n```\n# a comment\n```\n\n## Open "
                                                                     + "items\n\nNone.");
        assertThat(DocumentationText.titled(DocumentationText.sectionLevels(given), "ACME"))
                .startsWith("# ACME\n\n## Purpose and data\n\nA door.");
        // Sections already at '## ', under a title of the agent's or a level-one heading of its own: left as they
        // are.
        for (final String kept : List.of("# ACME\n\n## Purpose and data\nA door.",
                "## Purpose and data\n\n# Not a section\nA door.")) {
            assertThat(DocumentationText.sectionLevels(kept)).isEqualTo(kept);
        }
    }

    @Test
    void theSummaryGoesUnderTheAgentsOwnPurpose() {
        final String text = DocumentationText.belowSection("## Purpose and data\n\nProxy logs.\n\n## Field mapping\n\n"
                                                           + "T\n", "Purpose and data", "### Data surveyed\n\n- x\n");
        assertThat(text.indexOf("Proxy logs.")).isLessThan(text.indexOf("### Data surveyed"));
        assertThat(text.indexOf("### Data surveyed")).isLessThan(text.indexOf("## Field mapping"));
        assertThat(DocumentationText.belowSection("## Field mapping\n\nT\n", "Purpose and data", "### S\n"))
                .startsWith("## Purpose and data\n\n### S");
    }
}
