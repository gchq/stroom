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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.validation.XsltVersionHistory.Previous;
import stroom.ai.impl.tools.validation.XsltVersionHistory.Row;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The version history of an XSLT: a table in its description, one row a build, written when the build is
/// promoted and previewed as Unreleased until then; edits made by hand found and recorded as such. Ported from the
/// Stroom MCP server's `tests/test_version_history.py` (the XSLT history cases).
class TestXsltVersionHistory {

    private static final String CODE = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<xsl:stylesheet version=\"3.0\">"
                                       + "<xsl:template match=\"/\"/></xsl:stylesheet>";
    private static final String CHANGED = CODE.replace("match=\"/\"", "match=\"/Events\"");
    private static final Instant DAY1 = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void testABuildsSavesBecomeOneRowWhenItIsPromotedAndArePreviewedUntilThen() {
        final String first = saved("Acme events", null, "Created from its mapping", CODE, DAY1, "stroom-mcp 0.16.45");
        final String second = saved(first, new Previous(CODE, null, null), "Mapping changes: rule 'logon' replaced",
                CHANGED, DAY1, "stroom-mcp 0.16.45, model claude-haiku-5-5");
        // Asked for by the user: what a build has done, seen before it is released, without a row a save.
        assertThat(second).startsWith("Acme events\n\n## Version history");
        assertThat(XsltVersionHistory.history(second)).isEmpty();
        assertThat(second.lines().filter(line -> line.startsWith("| Unreleased |")).toList()).singleElement()
                .asString().contains("Created from its mapping; Mapping changes: rule 'logon' replaced");
        assertThat((List<?>) XsltVersionHistory.pendingOf(second).orElseThrow().get("entries")).hasSize(2);

        final String released = XsltVersionHistory.consolidate(second, CHANGED, DAY1);
        final List<Row> rows = XsltVersionHistory.history(released);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(List.of(row.version(), row.date(), row.author(), row.how()))
                    .containsExactly("1", "2026-10-10", "peter", "agent");
            assertThat(row.change()).isEqualTo("Created from its mapping; Mapping changes: rule 'logon' replaced");
            assertThat(row.by()).contains("model claude-haiku-5-5");
            assertThat(row.digest()).isEqualTo(XsltVersionHistory.digest(CHANGED));
        });
        assertThat(released).doesNotContain("| Unreleased |").startsWith("Acme events\n\n## Version history");
        assertThat(XsltVersionHistory.pendingOf(released)).isEmpty();
        // No hidden block left: no note.
        assertThat(released).doesNotContain(KeptMapping.NOTE);
    }

    @Test
    void testThePendingBlockIsHiddenAfterANoteAndReadBackExactly() {
        // Asked for by the user: the mapping (1,400 lines of JSON for one translation) buried the Documentation
        // tab. A comment can't hold '--'.
        final String described = saved("Acme events", null, "a -- b", CODE, DAY1, "x");
        final String[] split = described.split(Pattern.quote(KeptMapping.NOTE));
        assertThat(split[0]).startsWith("Acme events\n\n## Version history").contains("| Unreleased |");
        assertThat(split[1].replace("<!--", "").replace("-->", "")).doesNotContain("--");
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> entries = (List<Map<String, Object>>) XsltVersionHistory
                .pendingOf(described).orElseThrow().get("entries");
        assertThat(entries.getFirst().get("change")).isEqualTo("a -- b");
        assertThat(KeptMapping.withoutBlock(described, null)).isEqualTo(split[0].strip());
    }

    @Test
    void testAnEditMadeByHandSinceTheLastRowGetsARowOfItsOwn() {
        final String released = XsltVersionHistory.consolidate(saved("", null, "Created", CODE, DAY1, ""), CODE,
                DAY1);
        // In Stroom's editor.
        final String edited = CODE.replace("match=\"/\"", "match=\"/Records\"");
        final Previous previous = new Previous(edited, "jane", DAY2.toEpochMilli());
        final String again = XsltVersionHistory.consolidate(saved(released, previous, "Rule for Checkout events",
                CHANGED, DAY2, ""), CHANGED, DAY2);
        final List<Row> rows = XsltVersionHistory.history(again);
        assertThat(rows).extracting(row -> List.of(row.version(), row.author(), row.how())).containsExactly(
                List.of("1", "peter", "agent"), List.of("2", "jane", "by hand"), List.of("3", "peter", "agent"));
        assertThat(rows.get(1).change()).isEqualTo("Changed outside the agent (Stroom's editor)");
        assertThat(rows.get(1).date()).isEqualTo("2026-10-12");
        // Unchanged by hand: no such row.
        final String quiet = XsltVersionHistory.consolidate(saved(released, new Previous(CODE, null, null), "x",
                CHANGED, DAY2, ""), CHANGED, DAY2);
        assertThat(XsltVersionHistory.history(quiet)).extracting(Row::how).containsExactly("agent", "agent");
    }

    @Test
    void testAnXsltWrittenBeforeTheHistoryIsRecordedAsSuch() {
        final Previous previous = new Previous(CODE, "admin", DAY1.toEpochMilli());
        final List<Row> rows = XsltVersionHistory.history(XsltVersionHistory.consolidate(saved("", previous,
                "Working copy changed", CHANGED, DAY2, ""), CHANGED, DAY2));
        assertThat(List.of(rows.getFirst().author(), rows.getFirst().how(), rows.getFirst().change()))
                .containsExactly("admin", "by hand", "Written before the agent kept a history");
    }

    @Test
    void testAHistoryKeptInTheCodeBeforeIsMovedToTheDescriptionAndOutOfTheCode() {
        // 0.16.43 to 0.16.45 kept it as a comment at the start of the code, which every check comparing code had
        // to skip.
        final String commented = CODE.replace("<xsl:stylesheet", "<!-- stroom-mcp version history\n v1 | "
                                                                 + "2026-10-10 | peter | agent | Created | "
                                                                 + "stroom-mcp 0.16.44 | #1234abcd\nend of version "
                                                                 + "history -->\n<xsl:stylesheet");
        final String moved = XsltVersionHistory.adopt("Acme events", commented);
        assertThat(XsltVersionHistory.history(moved))
                .extracting(row -> List.of(row.version(), row.change(), row.digest()))
                .containsExactly(List.of("1", "Created", "1234abcd"));
        assertThat(XsltVersionHistory.strip(commented)).isEqualTo(CODE);
        // Once.
        assertThat(XsltVersionHistory.adopt(moved, commented)).isEqualTo(moved);
    }

    @Test
    void testARowHoldsNoColumnSeparatorOrLineBreak() {
        final String pending = XsltVersionHistory.withPending("", null, "p", "a -- b | c\nd", "", DAY1, CODE);
        assertThat(XsltVersionHistory.history(XsltVersionHistory.consolidate(pending, CODE, DAY1)).getFirst()
                .change()).isEqualTo("a -- b / c d");
    }

    @Test
    void testWhetherTheCodeIsWhatWasLastSavedIsKnown() {
        final String saved = XsltVersionHistory.withPending("", null, "p", "Created", "stroom-mcp 0.16.42", DAY1,
                CODE);
        assertThat(XsltVersionHistory.lastSaved(saved, CODE).orElseThrow().by()).isEqualTo("stroom-mcp 0.16.42");
        assertThat(XsltVersionHistory.untouched(saved, CODE)).contains(true);
        // The space between tags and the declaration are not what it does.
        assertThat(XsltVersionHistory.untouched(saved, CODE.replace("><xsl:template", ">\n  <xsl:template")))
                .contains(true);
        assertThat(XsltVersionHistory.untouched(saved, CHANGED)).contains(false);
        assertThat(XsltVersionHistory.untouched("", CODE)).isEmpty();
    }

    // The description as a save leaves it: the change pending, the history previewing it.
    private static String saved(final String description,
                                final Previous previous,
                                final String change,
                                final String code,
                                final Instant when,
                                final String by) {
        final String pending = XsltVersionHistory.withPending(description, previous, "peter", change, by, when, code);
        return XsltVersionHistory.preview(pending, code, when);
    }
}
