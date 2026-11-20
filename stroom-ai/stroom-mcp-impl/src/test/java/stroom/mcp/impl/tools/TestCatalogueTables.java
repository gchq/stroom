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

/// The source notes' "Event types" table, which holds every row.
class TestCatalogueTables {

    private static final String DOC = "'acme source notes'";

    @Test
    void theNotesHoldEveryEventTypeWithItsRecordsASectionPerActionElement() {
        final List<EventType> set = types(300);
        final Catalogue catalogue = new Catalogue(set, Catalogue.CatchAll.PROPOSED, Catalogue.Scope.NONE);

        final List<String> table = CatalogueTables.notesTable(catalogue, CatalogueTables.recordsOf(check(set)));

        assertThat(table).startsWith("## Event types").contains("### Process", CatalogueTables.NOTES_HEADER,
                "### Uncatalogued records");
        assertThat(table.stream().filter(line -> line.startsWith("| `Process-Code"))).hasSize(300)
                .allSatisfy(line -> assertThat(line).contains(" | 2 | "));
        // The catch-all last, in a section of its own.
        assertThat(table.stream().filter(line -> line.startsWith("| ")).toList().getLast())
                .startsWith("| `Unknown` Uncatalogued records | Translated as Unknown events, their fields kept as "
                            + "Data | records no event type recognises | 0 | proposed |");
        // Unchecked (a hand edit read back): the records left blank.
        assertThat(CatalogueTables.notesTable(catalogue, Map.of())).contains("| `Process-Code000` Process Code000 | "
                                                                             + "Test | every record |  | proposed; "
                                                                             + "medium; from the agent's knowledge |");
    }

    @Test
    void theNotesGroupEventTypesAsTheSummariesDoInTheirOrderWithStatusInShortWords() {
        final EventType logon = type("Authenticate-Logon", "Authenticate", "high", EventType.DOCUMENTATION)
                .confirmedByUser();
        final EventType export = type("Export-Report", "Export", "low", EventType.DRAFT);
        final EventType logoff = withoutDescription(type("Authenticate-Logoff", "Authenticate", "medium",
                EventType.AGENT_KNOWLEDGE));
        final Catalogue catalogue = new Catalogue(List.of(logon, export, logoff), new Catalogue.CatchAll(
                EventType.CONFIRMED, "the rest kept", null), Catalogue.Scope.NONE);

        final List<String> table = CatalogueTables.notesTable(catalogue, Map.of("Authenticate-Logon", 4,
                "Unknown", 3));

        assertThat(table.stream().filter(line -> line.startsWith("### ")).toList()).containsExactly(
                "### Authenticate", "### Export", "### Uncatalogued records");
        assertThat(table).contains("| `Authenticate-Logon` Authenticate Logon | Test | every record | 4 | confirmed; "
                                   + "high; from the documentation |",
                "| `Authenticate-Logoff` Authenticate Logoff | - | every record |  | proposed; medium; from the "
                + "agent's knowledge |",
                "| `Export-Report` Export Report | Test | every record |  | proposed; low; from the draft's seed |",
                "| `Unknown` Uncatalogued records | Translated as Unknown events, their fields kept as Data | records "
                + "no event type recognises | 3 | confirmed by the user: the rest kept |");
        assertThat(table.indexOf("### Export")).isGreaterThan(table.indexOf(
                "| `Authenticate-Logoff` Authenticate Logoff | - | every record |  | proposed; medium; from the "
                + "agent's "
                + "knowledge |"));
    }

    private static EventType type(final String typeId,
                                  final String eventDetail,
                                  final String confidence,
                                  final String basis) {
        return new EventType(typeId, typeId.replace('-', ' '), "Test", eventDetail, null, null, null, null, null,
                List.of(), false, basis, confidence, null, EventType.PROPOSED);
    }

    private static EventType withoutDescription(final EventType type) {
        return new EventType(type.typeId(), type.title(), null, type.eventDetail(), null, null, null, null, null,
                List.of(), false, type.basis(), type.confidence(), null, type.status());
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
