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

package stroom.ai.client;

import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.QueryTableContext;
import stroom.docref.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.shared.QuerySearchRequest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiTabSnapshots {

    private static final DocRef XSLT = new DocRef("XSLT", "1a2b", "Event translation");

    @Test
    void chipTextIsTheDocumentsNameWithUnsavedWhenDirty() {
        assertThat(AiTabSnapshots.chipText(XSLT, false)).isEqualTo("Tab: Event translation");
        assertThat(AiTabSnapshots.chipText(XSLT, true)).isEqualTo("Tab: Event translation (unsaved)");
    }

    @Test
    void chipTextNeverHasTheTabsUnsavedMarker() {
        // The tab's own label is "* Event translation" while it is dirty; the chip uses the document's name.
        assertThat(AiTabSnapshots.chipText(XSLT, true)).doesNotContain("*");
    }

    @Test
    void chipTextFallsBackToTheUuidWithoutAName() {
        assertThat(AiTabSnapshots.chipText(new DocRef("XSLT", "1a2b"), false)).isEqualTo("Tab: 1a2b");
        assertThat(AiTabSnapshots.chipText(null, false)).isEqualTo("Tab: Document");
    }

    @Test
    void chipTooltipSaysWhatIsShared() {
        assertThat(AiTabSnapshots.chipTooltip(XSLT))
                .contains("XSLT 'Event translation'")
                .contains("stop sharing");
    }

    @Test
    void theHashIsStable() {
        assertThat(snapshot("<xsl/>", false).getHash())
                .isEqualTo(snapshot("<xsl/>", false).getHash())
                .isNotBlank();
    }

    @Test
    void theHashChangesWithTheText() {
        assertThat(snapshot("<xsl/>", false).getHash()).isNotEqualTo(snapshot("<xsl />", false).getHash());
    }

    @Test
    void theHashChangesWithTheDirtyState() {
        assertThat(snapshot("<xsl/>", false).getHash()).isNotEqualTo(snapshot("<xsl/>", true).getHash());
    }

    @Test
    void theHashDoesNotConfuseMovingTextBetweenFields() {
        final AiTabSnapshot a = AiTabSnapshots.create(XSLT, "ab", "c", false, List.of(), List.of(), null);
        final AiTabSnapshot b = AiTabSnapshots.create(XSLT, "a", "bc", false, List.of(), List.of(), null);
        assertThat(a.getHash()).isNotEqualTo(b.getHash());
    }

    @Test
    void theHashChangesWithTheSearches() {
        final AiTabSnapshot first = withSearch("from Logons select User", "key1");
        assertThat(first.getHash()).isEqualTo(withSearch("from Logons select User", "key1").getHash());
        // The query edited, or run again.
        assertThat(first.getHash()).isNotEqualTo(withSearch("from Logons select Host", "key1").getHash());
        assertThat(first.getHash()).isNotEqualTo(withSearch("from Logons select User", "key2").getHash());
    }

    @Test
    void anUnchangedSnapshotIsSentAsItsHashOnly() {
        final AiTabSnapshot snapshot = snapshot("<xsl/>", true);
        final AiTabSnapshot sent = AiTabSnapshots.toSend(snapshot, snapshot.getHash());
        assertThat(sent.isHashOnly()).isTrue();
        assertThat(sent.getHash()).isEqualTo(snapshot.getHash());
        assertThat(sent.getDocRef()).isEqualTo(XSLT);
        assertThat(sent.isDirty()).isTrue();
    }

    @Test
    void aChangedOrNewSnapshotIsSentInFull() {
        final AiTabSnapshot snapshot = snapshot("<xsl/>", false);
        assertThat(AiTabSnapshots.toSend(snapshot, null)).isSameAs(snapshot);
        assertThat(AiTabSnapshots.toSend(snapshot, "something else")).isSameAs(snapshot);
        assertThat(AiTabSnapshots.toSend(null, "x")).isNull();
    }

    @Test
    void fnv1aOfNothingIsItsOffset() {
        assertThat(AiTabSnapshots.fnv1a("")).isEqualTo("cbf29ce484222325");
    }

    private static AiTabSnapshot snapshot(final String code, final boolean dirty) {
        return AiTabSnapshots.create(XSLT, "Event translation", "XSLT", dirty,
                List.of(new AiTabSnapshotPart(AiTabSnapshotPart.DATA, null, code)), List.of(), null);
    }

    private static AiTabSnapshot withSearch(final String query, final String queryKey) {
        final QuerySearchRequest results = QuerySearchRequest.builder()
                .query(query)
                .queryKey(new QueryKey(queryKey))
                .build();
        final AiTabSearch search = new AiTabSearch("Logons", null,
                new QueryTableContext("Query 'Logons'", null, results), null, List.of("User"), 10L, false);
        return AiTabSnapshots.create(new DocRef("Query", "q1", "Logons"), "Logons", "Query", false,
                List.of(new AiTabSnapshotPart(AiTabSnapshotPart.QUERY, null, query)), List.of(search), null);
    }
}
