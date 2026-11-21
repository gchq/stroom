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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// EventSource/System/Name per source type of a build's scope: told from a name and from each variant's, refused
/// where a source type is left out, another named, or the source types aren't told by exact values of a record's
/// field; the values mapped as the rows are tried, the first winning.
class TestSourceTypeSystemNames {

    private static final List<SourceTypeRows.Row> ROWS = List.of(
            new SourceTypeRows.Row("mail", SourceTypeRows.VALUE, "Exchange"),
            new SourceTypeRows.Row("chat", SourceTypeRows.VALUE, "Chat"),
            new SourceTypeRows.Row("files", SourceTypeRows.VALUE, "Files"),
            new SourceTypeRows.Row("chat", SourceTypeRows.VALUE, "Exchange"),
            new SourceTypeRows.Row("chat", SourceTypeRows.VALUE, "Calls"));

    @Test
    void namesKeyedByTheScopesSourceTypesAreEachSourceTypesOwn() {
        final SourceTypeScope scope = scope("Workload", ROWS);

        assertThat(SourceTypeSystemNames.of(Map.of("mail", "Mail", "chat", "Chat"), scope))
                .containsOnly(Map.entry("mail", "Mail"), Map.entry("chat", "Chat"));
        // One name, or each variant's: not this.
        assertThat(SourceTypeSystemNames.of("Acme", scope)).isEmpty();
        assertThat(SourceTypeSystemNames.of(Map.of("cef", "SecureGate"), scope)).isEmpty();
        assertThat(SourceTypeSystemNames.of(null, scope)).isEmpty();
        // Not a group of several source types.
        assertThat(SourceTypeSystemNames.of(Map.of("mail", "Mail"), new SourceTypeScope("b", "Workload", ROWS,
                List.of("mail")))).isEmpty();
        assertThat(SourceTypeSystemNames.of(Map.of("mail", "Mail", "chat", "Chat"), null)).isEmpty();
    }

    @Test
    void eachValueIsMappedToTheSourceTypeOfItsFirstRow() {
        final Map<String, String> names = new LinkedHashMap<>();
        names.put("mail", "Mail");
        names.put("chat", "Chat");

        // Exchange is mail's (its first row); files is outside the scope.
        assertThat(SourceTypeSystemNames.byValue(scope("Workload", ROWS), names))
                .containsExactly(Map.entry("Exchange", "Mail"), Map.entry("Chat", "Chat"),
                        Map.entry("Calls", "Chat"));
    }

    @Test
    void aSourceTypeLeftOutOrAnotherNamedIsRefused() {
        final SourceTypeScope scope = scope("Workload", ROWS);

        assertThatThrownBy(() -> SourceTypeSystemNames.of(Map.of("mail", "Mail"), scope))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("no name for ['chat']");
        assertThatThrownBy(() -> SourceTypeSystemNames.of(Map.of("mail", "Mail", "chat", " "), scope))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("no name for ['chat']");
        assertThatThrownBy(() -> SourceTypeSystemNames.of(Map.of("mail", "Mail", "chat", "Chat", "files", "F"),
                scope)).isInstanceOf(ToolRefusal.class).hasMessageContaining("'files'")
                .hasMessageContaining("chat, mail");
    }

    @Test
    void sourceTypesNotToldByExactValuesOfARecordsFieldTakeOneName() {
        final Map<String, String> both = Map.of("mail", "Mail", "chat", "Chat");
        final List<SourceTypeRows.Row> pattern = List.of(new SourceTypeRows.Row("mail", SourceTypeRows.VALUE, "M"),
                new SourceTypeRows.Row("chat", SourceTypeRows.PATTERN, "^Ch"));

        assertThatThrownBy(() -> SourceTypeSystemNames.of(both, scope("Workload", pattern)))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("chat isn't told by exact values")
                .hasMessageContaining("one name");
        assertThatThrownBy(() -> SourceTypeSystemNames.of(both, scope("meta:Product", ROWS)))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("meta attribute");
        assertThatThrownBy(() -> SourceTypeSystemNames.of(both, scope("event>logger", ROWS)))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("held values");
        // Nor offered when unnamed; offered where they can be.
        assertThat(SourceTypeSystemNames.unnamedNote(scope("Workload", pattern))).isEmpty();
        assertThat(SourceTypeSystemNames.unnamedNote(scope("Workload", ROWS))).hasValueSatisfying(note ->
                assertThat(note).contains("chat, mail", "one name for the group or each source type's own"));
    }

    @Test
    void theNamesAreWrittenFromTheSourceTypeFieldInTheSystemNamesPlace() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", List.of(Map.of("path", "EventTime/TimeCreated", "field", "time"),
                Map.of("path", "EventSource/System/Name", "value", "Mail")));

        SourceTypeSystemNames.apply(mapping, scope("Workload", ROWS), Map.of("mail", "Mail", "chat", "Chat"));

        // Read trimmed: a padded value, which the group's XSLT tells by its row, gets its name too.
        assertThat((List<?>) mapping.get("common")).hasSize(2).last().isEqualTo(Map.of("path",
                "EventSource/System/Name", "field", "Workload", "transform", "trim", "map", Map.of("Exchange",
                        "Mail", "Chat", "Chat", "Calls", "Chat")));
    }

    private static SourceTypeScope scope(final String field, final List<SourceTypeRows.Row> rows) {
        return new SourceTypeScope("b", field, rows, List.of("mail", "chat"));
    }
}
