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

package stroom.widget.util.client;

import com.google.gwt.view.client.ProvidesKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSelection {

    private static final ProvidesKey<Row> BY_ID = Row::id;

    @Test
    void comparesRowsByKey() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "old"), true);

        // An edited copy of the row (not equal to it) is the same row
        assertThat(selection.isSelected(new Row(1, "new"))).isTrue();
        assertThat(selection.isSelected(new Row(2, "old"))).isFalse();
    }

    @Test
    void comparesWithEqualsWithoutAKeyProvider() {
        final Selection<Row> selection = new Selection<>();
        selection.setSelected(new Row(1, "old"), true);

        assertThat(selection.isSelected(new Row(1, "old"))).isTrue();
        assertThat(selection.isSelected(new Row(1, "new"))).isFalse();
    }

    @Test
    void comparesWithEqualsWhenTheKeyIsNull() {
        final Selection<Row> selection = new Selection<>(row -> null);
        selection.setSelected(new Row(1, "old"), true);

        // A null key doesn't make every row the same row
        assertThat(selection.isSelected(new Row(1, "old"))).isTrue();
        assertThat(selection.isSelected(new Row(2, "other"))).isFalse();
    }

    @Test
    void selectingTheSameRowAgainStoresTheObjectGiven() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "old"), true);

        selection.setSelected(new Row(1, "new"), true);

        assertThat(selection.getSelectedItems()).containsExactly(new Row(1, "new"));
    }

    @Test
    void deselectingAnEditedCopyDeselectsTheRow() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "old"), true);

        selection.setSelected(new Row(1, "new"), false);

        assertThat(selection.size()).isZero();
    }

    @Test
    void aCopyKeepsTheKeyProvider() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "old"), true);

        final Selection<Row> copy = new Selection<>(selection);

        assertThat(copy.isSelected(new Row(1, "new"))).isTrue();
    }

    @Test
    void followRowsSwapsEachSelectedRowForItsNewObject() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "a"), true);
        selection.setSelected(new Row(3, "c"), true);

        final boolean changed = selection.followRows(List.of(new Row(1, "a2"), new Row(2, "b"), new Row(3, "c2")));

        assertThat(changed).isTrue();
        assertThat(selection.getSelectedItems()).containsExactly(new Row(3, "c2"), new Row(1, "a2"));
        // The most recent stays the most recent
        assertThat(selection.getSelected()).isEqualTo(new Row(3, "c2"));
    }

    @Test
    void followRowsKeepsRowsThatAreNotInTheNewRows() {
        // The rows may be only some of the page's (retainRows drops the rest)
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(9, "elsewhere"), true);
        selection.setSelected(new Row(1, "a"), true);

        selection.followRows(List.of(new Row(1, "a2")));

        assertThat(selection.getSelectedItems()).containsExactly(new Row(1, "a2"), new Row(9, "elsewhere"));
    }

    @Test
    void followRowsWithoutAKeyProviderSwapsForAnEqualRow() {
        final Selection<Named> selection = new Selection<>();
        final Named old = new Named("a");
        selection.setSelected(old, true);
        final Named fresh = new Named("a");

        selection.followRows(List.of(fresh));

        assertThat(selection.getSelected()).isSameAs(fresh);
    }

    @Test
    void followRowsReportsNoChangeForAnEqualRow() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "a"), true);

        // Fetched again, unchanged
        assertThat(selection.followRows(List.of(new Row(1, "a")))).isFalse();
    }

    @Test
    void followRowsReportsNoChangeWhenNothingMatches() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "a"), true);

        assertThat(selection.followRows(List.of(new Row(2, "b")))).isFalse();
        assertThat(new Selection<Row>(BY_ID).followRows(List.of(new Row(2, "b")))).isFalse();
    }

    @Test
    void followRowsIgnoresMissingRows() {
        // A page not yet loaded has null rows
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "a"), true);

        selection.followRows(Arrays.asList(null, new Row(1, "a2")));

        assertThat(selection.getSelectedItems()).containsExactly(new Row(1, "a2"));
    }

    @Test
    void retainRowsDeselectsRowsThatAreNotShown() {
        // e.g. a row now on another page, or hidden by a filter
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(9, "elsewhere"), true);
        selection.setSelected(new Row(1, "a"), true);

        final boolean changed = selection.retainRows(List.of(new Row(1, "a2"), new Row(2, "b")));

        assertThat(changed).isTrue();
        assertThat(selection.getSelectedItems()).containsExactly(new Row(1, "a"));
    }

    @Test
    void retainRowsReportsNoChangeWhenEverySelectedRowIsShown() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "a"), true);

        assertThat(selection.retainRows(List.of(new Row(1, "a2")))).isFalse();
        assertThat(new Selection<Row>(BY_ID).retainRows(List.of())).isFalse();
    }

    @Test
    void retainRowsOfNoRowsDeselectsEverything() {
        final Selection<Row> selection = new Selection<>(BY_ID);
        selection.setSelected(new Row(1, "a"), true);

        assertThat(selection.retainRows(List.of())).isTrue();
        assertThat(selection.size()).isZero();
    }

    @Test
    void retainRowsWithoutAKeyProviderComparesWithEquals() {
        final Selection<Named> selection = new Selection<>();
        selection.setSelected(new Named("a"), true);
        selection.setSelected(new Named("b"), true);

        selection.retainRows(Arrays.asList(null, new Named("a")));

        assertThat(selection.getSelectedItems()).containsExactly(new Named("a"));
    }

    @Test
    void rowsWithoutAKeyAreComparedWithEqualsAlone() {
        // A row class that overrides equals but not hashCode is still the same row
        final Selection<NoHash> selection = new Selection<>();
        selection.setSelected(new NoHash("a"), true);
        final NoHash fresh = new NoHash("a");

        selection.followRows(List.of(fresh));
        final boolean dropped = selection.retainRows(List.of(new NoHash("a")));

        assertThat(selection.getSelected()).isSameAs(fresh);
        assertThat(dropped).isFalse();
    }

    private record Row(int id, String value) {

    }

    // Equal by name, with the identity hash code
    private static final class NoHash {

        private final String name;

        private NoHash(final String name) {
            this.name = name;
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof final NoHash other && name.equals(other.name);
        }

        // Not consistent with equals, as a row class's may not be
        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    // Equal by name, but never the same object
    private static final class Named {

        private final String name;

        private Named(final String name) {
            this.name = name;
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof final Named named && name.equals(named.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
