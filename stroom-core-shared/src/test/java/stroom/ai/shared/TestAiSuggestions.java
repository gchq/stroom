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

package stroom.ai.shared;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiSuggestions {

    private static final DocRef DASHBOARD = new DocRef("Dashboard", "d1", "Logons");
    private static final DocRef QUERY = new DocRef("Query", "q1", "Failed logons");
    private static final DocRef XSLT = new DocRef("XSLT", "x1", "Event translation");
    private static final DocRef PIPELINE = new DocRef("Pipeline", "p1", "Acme-Events");

    @Test
    void aDashboardIsAskedAboutItsFirstTable() {
        assertThat(AiSuggestions.forTab(DASHBOARD, false, List.of(table("By user", 20L, false)), null))
                .containsExactly("What stands out in the 'By user' table?",
                        "Group the 'By user' table by its most useful column",
                        "Explain what this dashboard shows and where its data comes from");
        // Without tables, only what it shows.
        assertThat(AiSuggestions.forTab(DASHBOARD, false, List.of(), null))
                .containsExactly("Explain what this dashboard shows and where its data comes from");
    }

    @Test
    void aQueryWithNoResultsIsAskedWhy() {
        assertThat(AiSuggestions.forTab(QUERY, false, List.of(table("Table", 0L, false)), null))
                .first().isEqualTo("Why does this query return no results?");
        // Still searching, or with results, it isn't.
        assertThat(AiSuggestions.forTab(QUERY, false, List.of(table("Table", 0L, true)), null))
                .first().isEqualTo("What stands out in these results?");
        assertThat(AiSuggestions.forTab(QUERY, true, List.of(), null))
                .containsExactly("Explain what my changes to this query do",
                        "Count these results by their most useful field");
    }

    @Test
    void steppingIsAskedAboutTheSelectedElementAndItsErrors() {
        final AiSteppingElement xslt = new AiSteppingElement("translationFilter", "XSLTFilter", XSLT, true, false,
                2, 0, List.of());
        final AiSteppingContext stepping = new AiSteppingContext("ACME", 12L, "Raw Events", null, 1L, 1L,
                "translationFilter", List.of(xslt), List.of());

        assertThat(AiSuggestions.forTab(PIPELINE, false, List.of(), stepping))
                .containsExactly("Why is 'translationFilter' giving errors for this record, and how do I fix it?",
                        "Explain what 'translationFilter' does with this record",
                        "Which input fields does 'translationFilter' leave out of its output?");
    }

    @Test
    void unsavedCodeIsOfferedForReviewFirstAndAtMostThreeAreGiven() {
        assertThat(AiSuggestions.forTab(XSLT, true, List.of(), null))
                .hasSize(AiSuggestions.MAX_SUGGESTIONS)
                .first().isEqualTo("Review my unsaved changes to this XSLT");
        assertThat(AiSuggestions.forTab(null)).isEmpty();
    }

    @Test
    void longNamesAreShortened() {
        final String name = "n".repeat(100);
        assertThat(AiSuggestions.forTab(DASHBOARD, false, List.of(table(name, 1L, false)), null).getFirst())
                .contains("n".repeat(AiSuggestions.MAX_NAME_CHARS - 1) + "…'")
                .doesNotContain("n".repeat(AiSuggestions.MAX_NAME_CHARS));
    }

    @Test
    void theHintIsShortNamingTheTabSomeTablesAndWhatCanBeDone() {
        final List<AiTabSearch> tables = List.of(table("A", 1L, false), table("B", 1L, false),
                table("C", 1L, false), table("D", 1L, false));

        final String hint = AiSuggestions.tabHint(DASHBOARD, null, true, tables, null);

        assertThat(hint)
                .startsWith("The user has Dashboard 'Logons' attached to the chat (unsaved changes); tables: 'A', "
                            + "'B', 'C'.\nWith it, the assistant can ")
                .contains("'Apply to dashboard'")
                .doesNotContain("'D'");
        assertThat(hint.lines()).hasSize(2);
        assertThat(AiSuggestions.tabHint(null, null, false, null, null)).isEmpty();
    }

    @Test
    void documentsAreAskedAboutByWhatTheirTypeOffers() {
        // Onboarding first for a feed (asked for by the user).
        assertThat(AiSuggestions.forDocument(new DocRef("Feed", "f1", "ACME-EVENTS")))
                .containsExactly("Onboard 'ACME-EVENTS' (translate and index its data)",
                        "What data has 'ACME-EVENTS' received recently?",
                        "Are there processing errors for 'ACME-EVENTS'?");
        assertThat(AiSuggestions.forDocument(new DocRef("ElasticIndex", "e1", "logs")))
                .first().isEqualTo("What fields does 'logs' have?");
        assertThat(AiSuggestions.forDocument(new DocRef("Folder", "fo1", "Security")))
                .first().isEqualTo("What's in the 'Security' folder?");
        assertThat(AiSuggestions.forDocument(new DocRef("Folder", "fo1", "Security")))
                .contains("Onboard a new data source into the 'Security' folder");
        assertThat(AiSuggestions.forDocument(new DocRef("AnalyticRule", "a1", "Brute force")))
                .containsExactly("Explain 'Brute force'", "What uses 'Brute force'?");
        assertThat(AiSuggestions.forDocument(null)).isEmpty();
    }

    @Test
    void aFeedOrFolderTabOffersOnboarding() {
        assertThat(AiSuggestions.forTab(new DocRef("Feed", "f1", "ACME"), false, List.of(), null))
                .first().isEqualTo("Onboard this feed (translate and index its data)");
        assertThat(AiSuggestions.forTab(new DocRef("Folder", "fo1", "Security"), false, List.of(), null))
                .contains("Onboard a new data source into this folder");
        // And the model suggesting from the hint is told it can.
        assertThat(AiSuggestions.documentHint(new DocRef("Feed", "f1", "ACME"))).contains("onboard it");
        assertThat(AiSuggestions.tabHint(new DocRef("Folder", "fo1", "Security"), null, false, List.of(), null))
                .contains("onboard a new data source into it");
    }

    @Test
    void combiningTakesEachThingsMostLikelyQuestionFirst() {
        final List<String> feed = AiSuggestions.forDocument(new DocRef("Feed", "f1", "ACME"));
        final List<String> tab = AiSuggestions.forTab(DASHBOARD, false, List.of(), null);

        assertThat(AiSuggestions.combine(List.of(feed, tab)))
                .containsExactly(feed.get(0), tab.get(0), feed.get(1));
        // Repeats (e.g. two tables) are left out.
        assertThat(AiSuggestions.combine(List.of(AiSuggestions.forTable(), AiSuggestions.forTable())))
                .containsExactlyElementsOf(AiSuggestions.forTable());
        assertThat(AiSuggestions.combine(List.of())).isEmpty();
    }

    @Test
    void documentAndTableHintsAreTwoLines() {
        assertThat(AiSuggestions.documentHint(new DocRef("Feed", "f1", "ACME")))
                .startsWith("The user has attached Feed 'ACME' from the Explorer.\nWith it, the assistant can ")
                .contains("processing errors")
                .hasLineCount(2);
        assertThat(AiSuggestions.tableHint("Dashboard 'Logons' -> Table 'By user'"))
                .startsWith("The user has attached a table of results: Dashboard 'Logons' -> Table 'By user'.")
                .hasLineCount(2);
        assertThat(AiSuggestions.documentHint(null)).isEmpty();
    }

    @Test
    void theOutlineOfATabLeavesOutItsContent() {
        final AiTabSnapshot snapshot = new AiTabSnapshot(DASHBOARD, "Logons", null, true,
                List.of(new AiTabSnapshotPart(AiTabSnapshotPart.DASHBOARD, "application/json", "{}")),
                List.of(new AiTabSearch("By user", "c1", null, null, List.of("User"), 5L, false)), "note", null,
                "hash");

        final AiTabSnapshot outline = AiSuggestions.outline(snapshot);

        assertThat(outline.getDocRef()).isEqualTo(DASHBOARD);
        assertThat(outline.isDirty()).isTrue();
        assertThat(outline.getParts()).isNullOrEmpty();
        assertThat(outline.getNote()).isNull();
        assertThat(outline.getSearches()).singleElement().satisfies(search -> {
            assertThat(search.getName()).isEqualTo("By user");
            assertThat(search.getVisibleRows()).isEqualTo(5L);
            assertThat(search.getColumns()).isEmpty();
        });
        // The same suggestions come from it.
        assertThat(AiSuggestions.forTab(outline)).isEqualTo(AiSuggestions.forTab(snapshot));
        assertThat(AiSuggestions.outline(null)).isNull();
    }

    private static AiTabSearch table(final String name, final Long rows, final boolean searching) {
        return new AiTabSearch(name, "c-" + name, null, null, List.of(), rows, searching);
    }
}
