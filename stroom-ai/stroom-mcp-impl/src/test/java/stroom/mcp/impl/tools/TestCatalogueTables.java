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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.EventType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A set's table in the chat (whole, else cut, referring to the source notes) and the source notes' "Event types"
/// table, which holds every row.
class TestCatalogueTables {

    private static final String DOC = "'acme source notes'";

    @Test
    void aSetsTableFitsWholeInTheChat() {
        final List<EventType> set = types(20);

        assertThat(CatalogueTables.chatTable(set, check(set), DOC)).isEqualTo(CatalogueTables.setTable(set,
                check(set)));
    }

    @Test
    void aSetsTableTooLongForTheChatGivesItsFirstRowsAndWhereEveryRowIs() {
        final List<EventType> set = types(400);

        final String shown = CatalogueTables.chatTable(set, check(set), DOC);

        assertThat(shown.length()).isLessThanOrEqualTo(CatalogueTables.CHAT_TABLE_CHARS);
        assertThat(shown).startsWith(CatalogueTables.SET_HEADER).contains("Process-Code000")
                .doesNotContain("Process-Code399");
        final long rows = shown.lines().filter(line -> line.startsWith("| `")).count();
        assertThat(shown).endsWith("The first " + rows + " of these 400 event types; the other " + (400 - rows)
                                   + ", and every row, are in the Event types table of the source notes "
                                   + "'acme source notes'.");
    }

    @Test
    void theNotesTableHoldsEveryEventTypeWithItsRecords() {
        final List<EventType> set = types(300);
        final Catalogue catalogue = new Catalogue(set, Catalogue.CatchAll.PROPOSED, Catalogue.Scope.NONE);

        final List<String> table = CatalogueTables.notesTable(catalogue, CatalogueTables.recordsOf(check(set)));

        assertThat(table).contains(CatalogueTables.NOTES_HEADER);
        assertThat(table.stream().filter(line -> line.startsWith("| Process-Code"))).hasSize(300)
                .allSatisfy(line -> assertThat(line).contains(" | 2 | "));
        assertThat(table.stream().filter(line -> line.startsWith("| Unknown |")).findFirst().orElseThrow())
                .contains("| records no event type recognises | 0 |");
        // Unchecked (a hand edit read back): the records left blank.
        assertThat(CatalogueTables.notesTable(catalogue, Map.of())).contains("| Process-Code000 | Process Code000 | "
                                                                             + "every record |  | Process | agent "
                                                                             + "knowledge (medium), proposed |");
    }

    private static List<EventType> types(final int count) {
        final List<EventType> out = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            final String typeId = "Process-Code%03d".formatted(k);
            out.add(new EventType(typeId, typeId.replace('-', ' '), "Test", "Process", null, null, null, null, null,
                    List.of(), false, EventType.AGENT_KNOWLEDGE, "medium", null, EventType.PROPOSED));
        }
        return out;
    }

    private static CatalogueCheckResult check(final List<EventType> set) {
        final Map<String, CatalogueCheckResult.Count> counts = new LinkedHashMap<>();
        set.forEach(type -> counts.put(type.typeId(), new CatalogueCheckResult.Count(2, List.of(2))));
        return new CatalogueCheckResult(counts, List.of(), CatalogueCheckResult.Uncatalogued.NONE,
                CatalogueCheckResult.Unparsed.NONE, List.of(), List.of(), List.of(),
                set.stream().map(EventType::typeId).toList());
    }
}
