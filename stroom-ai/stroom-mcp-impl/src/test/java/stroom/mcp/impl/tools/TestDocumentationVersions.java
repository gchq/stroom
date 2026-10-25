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

import stroom.ai.impl.tools.KeptMapping;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The version control of a pipeline's documentation (the Stroom MCP server's `utils/versionlog.py`, and its test
/// in `tests/test_version_history.py`).
class TestDocumentationVersions {

    private static final Instant DAY1 = Instant.parse("2026-10-10T23:59:00Z");
    private static final Instant DAY2 = Instant.parse("2026-10-12T00:01:00Z");

    @Test
    void theDocsVersionControlTakesOneRowABuildAndKeepsAnOlderChangeLog() {
        final String old = "# Acme\n\n## Purpose\n\nText.\n\n## Change log\n- 2026-10-01: Created\n"
                           + "- 2026-10-03: Mapped CODE\n";
        final String first = DocumentationVersions.withPending("# Acme\n\n## Purpose\n\nNew text.", old,
                "Rule for Checkout events", "peter (agent)", "XSLT Acme @1234abcd", DAY1);
        final String second = DocumentationVersions.withPending("# Acme\n\n## Purpose\n\nNewer text.", first,
                "Field mapping regenerated", "peter (agent)", "XSLT Acme @5678abcd", DAY1);

        // While worked on: the older log's lines as rows, nothing added, the changes pending.
        assertThat(DocumentationVersions.rowsOf(second)).extracting(row -> row.get("change"))
                .containsExactly("Created", "Mapped CODE");
        assertThat(second).doesNotContain("## Change log").contains("Newer text.").doesNotContain("New text.");
        assertThat(DocumentationVersions.pendingOf(second)).hasSize(2);
        // Not in the Stroom MCP server, which ran the changes together with "; " (asked for by the user: a bullet
        // each, a line each).
        assertThat(second).contains("| • Rule for Checkout events<br>• Field mapping regenerated |");

        final String released = DocumentationVersions.consolidate(second, DAY2).orElseThrow();
        final List<Map<String, String>> rows = DocumentationVersions.rowsOf(released);
        assertThat(rows).extracting(row -> row.get("version")).containsExactly("1", "2", "3");
        assertThat(rows.getLast()).isEqualTo(Map.of("version", "3", "date", "2026-10-12", "by", "peter (agent)",
                "change", "• Rule for Checkout events<br>• Field mapping regenerated", "code",
                "XSLT Acme @5678abcd"));
        assertThat(DocumentationVersions.pendingOf(released)).isEmpty();
        assertThat(DocumentationVersions.consolidate(released, DAY2)).isEmpty();

        // Dated as the rest are: the day it ran broke it at midnight UTC.
        final String fresh = DocumentationVersions.withPending("# New", "", "Created", "peter (agent)", "", DAY1);
        assertThat(fresh).contains(DocumentationVersions.UNRELEASED)
                .contains("| Unreleased | 2026-10-10 | peter (agent) | Created |");
        // The preview is never read back as a released row.
        assertThat(DocumentationVersions.rowsOf(fresh)).isEmpty();
        final String releasedFresh = DocumentationVersions.consolidate(fresh, DAY1).orElseThrow();
        assertThat(DocumentationVersions.rowsOf(releasedFresh).getFirst().get("change")).isEqualTo("Created");
        assertThat(releasedFresh).doesNotContain("Unreleased");
    }

    @Test
    void theBodyIsTheTextWithoutItsVersionControlOrPendingChanges() {
        final String written = DocumentationVersions.withPending("# Acme\n\nBody.", "", "Created -- first",
                "peter (agent)", "", DAY1);

        // An HTML comment can't hold '--': spaced out, and put back when read.
        assertThat(written).contains("Created - - first").doesNotContain("Created -- first\"");
        assertThat(DocumentationVersions.pendingOf(written).getFirst().get("change")).isEqualTo("Created -- first");
        assertThat(DocumentationVersions.bodyOf(written)).isEqualTo("# Acme\n\nBody.");
        assertThat(DocumentationVersions.bodyOf(null)).isEmpty();
    }

    @Test
    void aRowHoldsNoColumnSeparatorOrLineBreak() {
        final String pending = DocumentationVersions.withPending("# A", "", "a | b\nc", "p", "", DAY1);
        final String released = DocumentationVersions.consolidate(pending, DAY1).orElseThrow();

        assertThat(released).contains("| 1 | 2026-10-10 | p | a \\| b c |  |");
        assertThat(DocumentationVersions.rowsOf(released).getFirst().get("change")).isEqualTo("a | b c");
    }

    @Test
    void theCodeARowDescribesIsTheXsltItsVersionAndTheMappingsDigest() {
        final KeptMappings.KeptXslt kept = new KeptMappings.KeptXslt(new KeptMapping.Kept("translation",
                Map.of("mapping", Map.of())), "translationFilter", XsltDoc.builder().uuid("x").name("Acme")
                .version("1234abcd-ef").data("<xsl:stylesheet/>").build());

        assertThat(DocumentationVersions.codeOf(kept)).isEqualTo("XSLT Acme @1234abcd, translation mapping "
                                                                 + BuildChecks.mappingDigest(kept).substring(0, 8));
        assertThat(DocumentationVersions.codeOf(null)).isEmpty();
    }
}
