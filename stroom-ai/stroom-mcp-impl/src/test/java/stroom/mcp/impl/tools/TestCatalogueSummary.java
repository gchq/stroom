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
import stroom.ai.impl.tools.catalogue.CatalogueDiff;
import stroom.ai.impl.tools.catalogue.EventType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A set's summary (decided by the user 2026-11-20, for every confidence): grouped by what happened, a few TypeIds
/// each with their records, then those worth a look by a generic rule, the amended and the doubtful among them.
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
                            + "source notes 'acme source notes'; ask about any of them in the chat.")
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
        assertThat(summary).endsWith("And 30 more worth a look, in the source notes 'acme source notes'.");
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

    @Test
    void aMediumSetIsASummaryTooEveryOneWorthALookSayingWhyItIsDoubtful() {
        // Decided by the user 2026-11-20: medium and low sets are summaries as the high one is.
        final List<EventType> set = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int k = 0; k < 14; k++) {
            final String typeId = "Process-Code%02d".formatted(k);
            set.add(confident(type(typeId, "Process", null), "medium"));
            counts.put(typeId, 3);
        }
        set.add(doubted(confident(type("Update-Password", "Update", null), "medium"), "only in the vendor's docs"));
        counts.put("Update-Password", 0);
        set.add(saidOne(confident(type("Process-Batch", "Process", null), "medium"),
                "every batch is translated alike"));
        counts.put("Process-Batch", 5);

        final String summary = CatalogueSummary.summary(set, check(set, counts), DOC);
        final List<CatalogueSummary.Unusual> worth = CatalogueSummary.worthALook(set, check(set, counts),
                CatalogueSummary.Amendments.NONE);

        assertThat(summary).startsWith("**16 medium-confidence event types**, by what happened: each is doubtful, so "
                                       + "each is worth a look below.")
                .contains("- Process: 15 event types, 47 records")
                .contains("**Worth a look** (16 event types):")
                .doesNotContain(CatalogueTables.SET_HEADER + "\n")
                .endsWith("And 6 more worth a look, in the source notes 'acme source notes'.");
        assertThat(summary.lines().filter(line -> line.startsWith("| `"))).hasSize(CatalogueSummary.MOST_LISTED);
        // Those the agent's doubt or a rule picks out first, then those doubtful by their confidence alone.
        assertThat(worth).extracting(one -> one.type().typeId()).startsWith("Update-Password", "Process-Batch",
                "Process-Code00");
        assertThat(whyOf(worth, "Update-Password")).containsExactly("medium confidence: only in the vendor's docs",
                "no record of it in the sample", "the only one under Update");
        assertThat(whyOf(worth, "Process-Batch")).containsExactly("medium confidence",
                "one event type because every batch is translated alike");
        assertThat(whyOf(worth, "Process-Code03")).containsExactly("medium confidence");
    }

    @Test
    void theEventTypesAmendedSinceTheSetWasShownAreListedFirstSayingWhatChanged() {
        final List<EventType> set = List.of(type("Authenticate-Logon", "Authenticate", "Logon"),
                type("Authenticate-Logoff", "Authenticate", "Logoff"));
        final CatalogueSummary.Amendments amendments = CatalogueSummary.Amendments.NONE
                .with(new CatalogueDiff.Diff(List.of(), List.of(new CatalogueDiff.Changed("Authenticate-Logoff",
                        List.of("title"))), List.of("Authenticate-Timeout"), false))
                .with(new CatalogueDiff.Diff(List.of(), List.of(new CatalogueDiff.Changed("Authenticate-Logoff",
                        List.of("recognised by", "title"))), List.of(), false));

        final String summary = CatalogueSummary.summary(set, check(set, Map.of("Authenticate-Logon", 2,
                "Authenticate-Logoff", 2)), DOC, amendments);

        assertThat(summary).contains("Removed, as amended: Authenticate-Timeout.",
                "**Worth a look** (1 event type):",
                "| `Authenticate-Logoff` Authenticate Logoff | every record | 2 | Authenticate Logoff | amended: "
                + "title, "
                + "recognised by |");
        // Kept in the confirmation's marks between calls.
        assertThat(CatalogueSummary.Amendments.fromJson(amendments.toJson())).isEqualTo(amendments);
    }

    @Test
    void anEventTypeAddedThenRemovedByAmendmentsWasNeverThere() {
        final CatalogueSummary.Amendments amendments = CatalogueSummary.Amendments.NONE
                .with(new CatalogueDiff.Diff(List.of("Update-Password"), List.of(), List.of(), false))
                .with(new CatalogueDiff.Diff(List.of(), List.of(), List.of("Update-Password"), false));

        assertThat(amendments.none()).isTrue();
        assertThat(CatalogueSummary.Amendments.NONE.with(new CatalogueDiff.Diff(List.of("Update-Password"),
                List.of(), List.of(), false)).of("update-password")).containsExactly("added");
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

    private static EventType confident(final EventType type, final String confidence) {
        return new EventType(type.typeId(), type.title(), type.description(), type.eventDetail(), type.action(),
                null, null, null, null, List.of(), false, type.basis(), confidence, type.oneTypeBecause(),
                type.status(), type.doubt());
    }

    private static EventType doubted(final EventType type, final String doubt) {
        return new EventType(type.typeId(), type.title(), type.description(), type.eventDetail(), type.action(),
                null, null, null, null, List.of(), false, type.basis(), type.confidence(), type.oneTypeBecause(),
                type.status(), doubt);
    }

    private static EventType saidOne(final EventType type, final String because) {
        return new EventType(type.typeId(), type.title(), type.description(), type.eventDetail(), type.action(),
                null, null, null, null, List.of(), false, type.basis(), type.confidence(), because, type.status(),
                type.doubt());
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
