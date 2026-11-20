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

import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.EventType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The high-confidence event types' summary (decided by the user 2026-11-20): grouped by what happened, a few TypeIds
/// each with their records, then those worth a look by a generic rule.
class TestCatalogueSummary {

    private static final String DOC = "'acme source notes'";

    @Test
    void eventTypesAreGroupedByTheirActionElementWithTheirRecords() {
        final List<EventType> set = List.of(type("Authenticate-Logon", "Authenticate", "Logon"),
                type("Process-Request", "Process", null),
                type("Authenticate-Logoff", "Authenticate", "Logoff"));
        final CatalogueCheckResult check = check(set, Map.of("Authenticate-Logon", 8, "Process-Request", 3,
                "Authenticate-Logoff", 5));

        final List<CatalogueSummary.Group> groups = CatalogueSummary.groups(set, check);

        assertThat(groups).extracting(CatalogueSummary.Group::whatHappened).containsExactly("Authenticate",
                "Process");
        assertThat(groups.getFirst().types()).extracting(EventType::typeId).containsExactly("Authenticate-Logon",
                "Authenticate-Logoff");
        assertThat(groups.getFirst().records()).isEqualTo(13);
        assertThat(CatalogueSummary.summary(set, check, DOC))
                .startsWith("**3 high-confidence event types**, by what happened. Each one, row by row, is in the "
                            + "Event types table of the source notes 'acme source notes'.")
                .contains("- Authenticate: 2 event types, 13 records; Authenticate-Logon (8), Authenticate-Logoff (5)")
                .contains("- Process: 1 event type, 3 records; Process-Request (3)")
                .contains("Worth a look: none.")
                .doesNotContain(CatalogueTables.SET_HEADER);
    }

    @Test
    void aLargeGroupNamesItsCommonestTypeIdsAndCountsTheRest() {
        final List<EventType> set = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int k = 0; k < 40; k++) {
            final String typeId = "Process-Code%02d".formatted(k);
            set.add(type(typeId, "Process", null));
            counts.put(typeId, k == 7
                    ? 50
                    : 2);
        }

        assertThat(CatalogueSummary.summary(set, check(set, counts), DOC))
                .contains("- Process: 40 event types, 128 records; e.g. Process-Code07 (50), Process-Code00 (2), "
                          + "Process-Code01 (2) and 37 more");
    }

    @Test
    void eventTypesWorthALookAreListedRowByRowSayingWhy() {
        final List<EventType> set = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int k = 0; k < 8; k++) {
            set.add(type("Authenticate-Logon%d".formatted(k), "Authenticate", "Logon"));
            counts.put("Authenticate-Logon%d".formatted(k), 4);
        }
        set.add(type("Authenticate-Reset", "Authenticate", "Other"));
        counts.put("Authenticate-Reset", 4);
        set.add(type("Export-Report", "Export", null));
        counts.put("Export-Report", 1);
        set.add(type("Delete-Records", "Delete", null));
        // From documentation: not in the sample.
        counts.put("Delete-Records", 0);
        set.add(withoutDescription(type("Unknown-Thing", "Process", null)));
        counts.put("Unknown-Thing", 2);
        set.add(type("Process-Nothing", null, null));
        counts.put("Process-Nothing", 1);

        final List<CatalogueSummary.Unusual> unusual = CatalogueSummary.unusual(set, check(set, counts));

        assertThat(unusual).extracting(one -> one.type().typeId()).containsExactly("Authenticate-Reset",
                "Export-Report", "Delete-Records", "Unknown-Thing", "Process-Nothing");
        assertThat(whyOf(unusual, "Authenticate-Reset")).containsExactly("its action is Other");
        assertThat(whyOf(unusual, "Export-Report")).containsExactly("the only one under Export");
        assertThat(whyOf(unusual, "Delete-Records")).containsExactly("no record of it in the sample",
                "the only one under Delete");
        assertThat(whyOf(unusual, "Unknown-Thing")).containsExactly("its TypeId's verb is Unknown",
                "the only one under Process", "no description of its own");
        assertThat(whyOf(unusual, "Process-Nothing")).containsExactly(
                "no action element: its records are written as Unknown");

        final String summary = CatalogueSummary.summary(set, check(set, counts), DOC);
        assertThat(summary).contains("**Worth a look** (5 event types):", CatalogueSummary.UNUSUAL_HEADER,
                "| `Delete-Records` Delete Records | every record | 0 (not in the sample) | Delete | no record of it "
                + "in the sample; the only one under Delete |");
    }

    @Test
    void aLoneActionElementIsWorthALookOnlyInALargeSet() {
        // In a small set every element is lone, and the summary names each type already.
        final List<EventType> set = List.of(type("Authenticate-Logon", "Authenticate", "Logon"),
                type("Authenticate-Logoff", "Authenticate", "Logoff"), type("Export-Report", "Export", null));

        assertThat(CatalogueSummary.unusual(set, check(set, Map.of("Authenticate-Logon", 1, "Authenticate-Logoff",
                1, "Export-Report", 1)))).isEmpty();
    }

    @Test
    void aDroppedEventTypeIsGroupedApartAndWorthALook() {
        final List<EventType> set = List.of(type("Authenticate-Logon", "Authenticate", "Logon"),
                dropped(type("Process-Heartbeat", "Process", null)));
        final CatalogueCheckResult check = check(set, Map.of("Authenticate-Logon", 2, "Process-Heartbeat", 9));

        assertThat(CatalogueSummary.groups(set, check)).extracting(CatalogueSummary.Group::whatHappened)
                .containsExactly("Authenticate", "dropped (not translated)");
        assertThat(CatalogueSummary.unusual(set, check)).singleElement().satisfies(one -> assertThat(one.why())
                .containsExactly("its records are dropped"));
    }

    @Test
    void manyWorthALookAreListedUpToALimitAndTheRestCounted() {
        final List<EventType> set = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int k = 0; k < 40; k++) {
            set.add(type("Authenticate-Doc%02d".formatted(k), "Authenticate", "Logon"));
            counts.put("Authenticate-Doc%02d".formatted(k), 0);
        }

        final String summary = CatalogueSummary.summary(set, check(set, counts), DOC);

        assertThat(summary.lines().filter(line -> line.startsWith("| `Authenticate-Doc"))).hasSize(
                CatalogueSummary.MOST_LISTED);
        assertThat(summary).endsWith("And 15 more worth a look, in the source notes 'acme source notes'.");
    }

    @Test
    void threeHundredEventTypesAreSummarisedShort() {
        final List<EventType> set = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final List<String> elements = List.of("Authenticate", "Process", "Update", "Create", "Delete", "View");
        for (int k = 0; k < 300; k++) {
            final String typeId = "%s-Code%03d".formatted(elements.get(k % elements.size()), k);
            set.add(type(typeId, elements.get(k % elements.size()), null));
            counts.put(typeId, 2);
        }

        final String summary = CatalogueSummary.summary(set, check(set, counts), DOC);

        assertThat(summary.length()).as("well under the 40,000 characters a client takes: %s", summary)
                .isLessThan(2_000);
        assertThat(summary).contains("- Authenticate: 50 event types, 100 records; e.g. Authenticate-Code000 (2)");
    }

    private static List<String> whyOf(final List<CatalogueSummary.Unusual> unusual, final String typeId) {
        return unusual.stream()
                .filter(one -> typeId.equals(one.type().typeId()))
                .findFirst()
                .orElseThrow()
                .why();
    }

    // A high-confidence event type recognised by every record (the summary reads its meaning, not its conditions).
    private static EventType type(final String typeId, final String eventDetail, final String action) {
        return new EventType(typeId, typeId.replace('-', ' '), "Test: " + typeId, eventDetail, action, null, null,
                null, null, List.of(), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
    }

    private static EventType withoutDescription(final EventType type) {
        return new EventType(type.typeId(), type.title(), null, type.eventDetail(), type.action(), null, null, null,
                null, List.of(), false, type.basis(), type.confidence(), null, type.status());
    }

    private static EventType dropped(final EventType type) {
        return new EventType(type.typeId(), type.title(), type.description(), type.eventDetail(), type.action(),
                null, null, null, null, List.of(), true, type.basis(), type.confidence(), null, type.status());
    }

    private static CatalogueCheckResult check(final List<EventType> set, final Map<String, Integer> records) {
        final Map<String, CatalogueCheckResult.Count> counts = new LinkedHashMap<>();
        records.forEach((typeId, count) -> counts.put(typeId, new CatalogueCheckResult.Count(count, List.of(count))));
        return new CatalogueCheckResult(counts, List.of(), CatalogueCheckResult.Uncatalogued.NONE,
                CatalogueCheckResult.Unparsed.NONE, List.of(), List.of(), List.of(),
                set.stream().map(EventType::typeId).toList());
    }
}
