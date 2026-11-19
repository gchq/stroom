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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestMultiSelectionModelImpl {

    @Test
    void setSelectionFiresWhenTheSelectionChanges() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        final List<SelectionType> fired = listen(model);

        model.setSelection(selectionOf("a", "b"), new SelectionType());

        assertThat(fired).hasSize(1);
        assertThat(model.getSelectedItems()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void setSelectionDoesNotFireForTheSameSelection() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelection(selectionOf("a", "b"), new SelectionType());
        final List<SelectionType> fired = listen(model);

        // The same items again (it once fired for any selection set after a first change)
        model.setSelection(selectionOf("a", "b"), new SelectionType());

        assertThat(fired).isEmpty();
    }

    @Test
    void setSelectionFiresWhenAnItemIsDropped() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelection(selectionOf("a", "b"), new SelectionType());
        final List<SelectionType> fired = listen(model);

        model.setSelection(selectionOf("a"), new SelectionType());

        assertThat(fired).hasSize(1);
        assertThat(model.getSelectedItems()).containsExactly("a");
    }

    @Test
    void setSelectionFiresForADoubleSelectOfTheSameSelection() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelection(selectionOf("a"), new SelectionType());
        final List<SelectionType> fired = listen(model);
        final SelectionType doubleSelect = new SelectionType(true, false, false, false, false);

        model.setSelection(selectionOf("a"), doubleSelect);

        assertThat(fired).containsExactly(doubleSelect);
    }

    @Test
    void setSelectedFiresOnlyWhenTheItemChanges() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        final List<SelectionType> fired = listen(model);

        model.setSelected("a", true);
        model.setSelected("a", true);

        assertThat(fired).hasSize(1);
        assertThat(model.isSelected("a")).isTrue();
    }

    @Test
    void clearFiresOnlyWhenSomethingIsSelected() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        final List<SelectionType> fired = listen(model);

        model.clear();
        assertThat(fired).isEmpty();

        model.setSelected("a", true);
        model.clear();

        assertThat(fired).hasSize(2);
        assertThat(model.getSelectedItems()).isEmpty();
    }

    @Test
    void anEditedCopyOfASelectedRowIsSelected() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "old"));

        assertThat(model.isSelected(new Row(1, "new"))).isTrue();
        assertThat(model.getKey(new Row(1, "new"))).isEqualTo(1);
    }

    @Test
    void aKeyProviderSetLaterKeepsTheSelection() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setSelected(new Row(1, "old"));

        model.setKeyProvider(Row::id);

        assertThat(model.isSelected(new Row(1, "new"))).isTrue();
        assertThat(model.getSelection().isSelected(new Row(1, "new"))).isTrue();
    }

    @Test
    void selectingTheOnlySelectedRowAgainUnchangedStoresItWithoutFiring() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        final Row first = new Row(1, "old");
        model.setSelected(first);
        final List<SelectionType> fired = listenToRows(model);

        final Row again = new Row(1, "old");
        model.setSelected(again, new SelectionType(), true);

        assertThat(model.getSelected()).isSameAs(again);
        assertThat(fired).isEmpty();
    }

    @Test
    void selectingTheOnlySelectedRowAgainChangedFires() {
        // As selecting another row would, so what shows the selected row shows its change
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "old"));
        final List<SelectionType> fired = listenToRows(model);

        model.setSelected(new Row(1, "new"), new SelectionType(), true);
        model.setSelected(new Row(1, "new"), true);

        assertThat(model.getSelected()).isEqualTo(new Row(1, "new"));
        assertThat(fired).hasSize(1);
    }

    @Test
    void followRowsFiresOnceWhenTheSelectedRowHasChanged() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "old"));
        final List<SelectionType> fired = listenToRows(model);

        model.followRows(List.of(new Row(1, "new"), new Row(2, "other")));
        // Owed until the page settles
        assertThat(fired).isEmpty();
        model.settle(List.of(new Row(1, "new"), new Row(2, "other")), 0, false);

        assertThat(model.getSelected()).isEqualTo(new Row(1, "new"));
        assertThat(fired).hasSize(1);
        assertThat(fired.get(0).isDoubleSelect()).isFalse();
    }

    @Test
    void followRowsFiresNothingForUnchangedOrOtherRows() {
        // e.g. a list that polls: nothing that follows its selection is reloaded
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "same"));
        final List<SelectionType> fired = listenToRows(model);
        final List<Object> changes = new ArrayList<>();
        model.addSelectionChangeHandler(changes::add);

        final Row fetched = new Row(1, "same");
        model.followRows(List.of(fetched, new Row(2, "changed")));

        assertThat(model.getSelected()).isSameAs(fetched);
        assertThat(fired).isEmpty();
        assertThat(changes).isEmpty();
    }

    @Test
    void setSelectionComparesByKey() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "old"));
        final List<SelectionType> fired = listenToRows(model);

        // A selection of the same row's new object, made without the key (it takes the model's)
        final Selection<Row> same = new Selection<>();
        same.setSelected(new Row(1, "new"), true);
        model.setSelection(same, new SelectionType());

        assertThat(fired).isEmpty();
        assertThat(model.isSelected(new Row(1, "newer"))).isTrue();
    }

    @Test
    void retainRowsFiresOnceWhenARowIsDropped() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelectedItems(List.of(new Row(1, "a"), new Row(2, "b")));
        final List<SelectionType> fired = listenToRows(model);
        final List<Object> changes = new ArrayList<>();
        model.addSelectionChangeHandler(changes::add);

        model.retainRows(List.of(new Row(2, "b"), new Row(3, "c")));

        assertThat(model.getSelectedItems()).containsExactly(new Row(2, "b"));
        assertThat(fired).hasSize(1);
        assertThat(changes).hasSize(1);
    }

    @Test
    void retainRowsFiresNothingWhenEveryRowIsShown() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "a"));
        final List<SelectionType> fired = listenToRows(model);

        model.retainRows(List.of(new Row(1, "a"), new Row(2, "b")));

        assertThat(model.getSelected()).isEqualTo(new Row(1, "a"));
        assertThat(fired).isEmpty();
    }

    @Test
    void settleDeselectsRowsNotShown() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelectedItems(List.of("a", "x"));
        final List<SelectionType> fired = listen(model);

        model.settle(List.of("a", "b"), 0, false);

        assertThat(model.getSelectedItems()).containsExactly("a");
        assertThat(fired).hasSize(1);
    }

    @Test
    void settleKeepsRowsNotShownForAPicker() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelected("x");

        model.settle(List.of("a", "b"), 0, true);

        assertThat(model.getSelected()).isEqualTo("x");
    }

    @Test
    void removalSelectsTheRowNowInItsPlace() {
        final List<String> page = List.of("a", "b", "c", "d");
        final MultiSelectionModelImpl<String> model = onPage(page, 0);
        model.setSelected("b");
        final List<SelectionType> fired = listen(model);

        model.selectNextAfterRemove(List.of("b"));
        model.settle(List.of("a", "c", "d"), 0, false);

        assertThat(model.getSelected()).isEqualTo("c");
        assertThat(fired).hasSize(1);
    }

    @Test
    void removalOfSeveralSelectsTheRowInPlaceOfTheFirst() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b", "c", "d", "e"), 0);
        model.setSelectedItems(List.of("d", "b"));

        // In any order
        model.selectNextAfterRemove(List.of("d", "b"));
        model.settle(List.of("a", "c", "e"), 0, false);

        assertThat(model.getSelectedItems()).containsExactly("c");
    }

    @Test
    void removalOfTheLastRowSelectsTheNewLastRow() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b", "c"), 0);
        model.setSelected("c");

        model.selectNextAfterRemove(List.of("c"));
        model.settle(List.of("a", "b"), 0, false);

        assertThat(model.getSelected()).isEqualTo("b");
    }

    @Test
    void removalOfEveryRowSelectsNothing() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a"), 0);
        model.setSelected("a");

        model.selectNextAfterRemove(List.of("a"));
        model.settle(List.of(), 0, false);

        assertThat(model.getSelectedCount()).isZero();
    }

    @Test
    void removalOnALaterPageUsesTheAbsoluteIndex() {
        // Rows 5 to 8 shown; "g" (7) removed, and the page moved back to start at 4
        final MultiSelectionModelImpl<String> model = onPage(List.of("f", "g", "h", "i"), 5);
        model.setSelected("g");

        model.selectNextAfterRemove(List.of("g"));
        model.settle(List.of("e", "f", "h", "i"), 4, false);

        assertThat(model.getSelected()).isEqualTo("h");
    }

    @Test
    void aRowRemovedButStillShownStaysSelected() {
        // e.g. a user whose access is revoked, still listed as inactive
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b", "c"), 0);
        model.setSelected("b");

        model.selectNextAfterRemove(List.of("b"));
        model.settle(List.of("a", "b", "c"), 0, false);

        assertThat(model.getSelected()).isEqualTo("b");
        assertThat(model.isRemovalPending()).isFalse();
    }

    @Test
    void removalOfRowsNotShownLeavesNothingPending() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b"), 0);

        model.selectNextAfterRemove(List.of("x"));

        assertThat(model.isRemovalPending()).isFalse();
    }

    @Test
    void selectingARowCancelsTheRemoval() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b", "c"), 0);
        model.setSelected("b");
        model.selectNextAfterRemove(List.of("b"));

        model.setSelected("a");
        model.settle(List.of("a", "c"), 0, false);

        assertThat(model.getSelected()).isEqualTo("a");
    }

    @Test
    void aRowWaitingToBeShownIsSelectedWhenItIs() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "a"));

        model.selectWhenShown(row -> row.id() == 3);
        model.settle(List.of(new Row(1, "a"), new Row(3, "new")), 0, false);

        assertThat(model.getSelected()).isEqualTo(new Row(3, "new"));
    }

    @Test
    void aRowWaitingToBeShownOnAnotherPageSelectsNothing() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "a"));

        model.selectWhenShown(row -> row.id() == 99);
        model.settle(List.of(new Row(1, "a")), 0, false);

        assertThat(model.getSelectedCount()).isZero();
    }

    @Test
    void aListThatSelectsFirstSelectsTheFirstRowWhenNoneShownIs() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelectFirst(true);
        model.setSelected("x");
        final List<SelectionType> fired = listen(model);

        // e.g. another page
        model.settle(List.of("a", "b"), 0, false);

        assertThat(model.getSelected()).isEqualTo("a");
        assertThat(fired).hasSize(1);
    }

    @Test
    void aListThatSelectsFirstKeepsARowShown() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelectFirst(true);
        model.setSelected("b");

        model.settle(List.of("a", "b"), 0, false);

        assertThat(model.getSelected()).isEqualTo("b");
    }

    @Test
    void aListThatSelectsFirstSelectsTheRowInPlaceOfOneRemoved() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b", "c"), 0);
        model.setSelectFirst(true);
        model.setSelected("b");

        model.selectNextAfterRemove(List.of("b"));
        model.settle(List.of("a", "c"), 0, false);

        assertThat(model.getSelected()).isEqualTo("c");
    }

    @Test
    void aListThatSelectsFirstSelectsTheFirstRowWhenARowWaitingIsNotShown() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelectFirst(true);

        model.selectWhenShown("z"::equals);
        model.settle(List.of("a", "b"), 0, false);

        assertThat(model.getSelected()).isEqualTo("a");
    }

    @Test
    void aListThatSelectsFirstSelectsNothingWhenEmpty() {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setSelectFirst(true);
        model.setSelected("x");

        model.settle(List.of(), 0, false);

        assertThat(model.getSelectedCount()).isZero();
    }

    @Test
    void aChangedRowAndADroppedRowFireOneEvent() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelectedItems(List.of(new Row(1, "old"), new Row(9, "hidden")));
        final List<SelectionType> fired = listenToRows(model);
        final List<Object> changes = new ArrayList<>();
        model.addSelectionChangeHandler(changes::add);

        final List<Row> page = List.of(new Row(1, "new"), new Row(2, "other"));
        model.followRows(page);
        model.settle(page, 0, false);

        assertThat(model.getSelectedItems()).containsExactly(new Row(1, "new"));
        assertThat(fired).hasSize(1);
        assertThat(changes).hasSize(1);
    }

    @Test
    void aSelectionMadeAfterAChangedRowFiresOnlyItsOwnEvent() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "old"));
        final List<SelectionType> fired = listenToRows(model);

        final List<Row> page = List.of(new Row(1, "new"), new Row(2, "other"));
        model.followRows(page);
        // e.g. a list held in the client choosing a row itself
        model.setSelected(new Row(2, "other"));
        model.settle(page, 0, false);

        assertThat(fired).hasSize(1);
    }

    @Test
    void flushFollowedFiresTheOwedEventOnce() {
        final MultiSelectionModelImpl<Row> model = new MultiSelectionModelImpl<>();
        model.setKeyProvider(Row::id);
        model.setSelected(new Row(1, "old"));
        final List<SelectionType> fired = listenToRows(model);

        model.followRows(List.of(new Row(1, "new")));
        model.flushFollowed();
        model.flushFollowed();

        assertThat(fired).hasSize(1);
    }

    @Test
    void aPickerKeepsHiddenChoicesWhenARemovedRowIsStillShown() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b"), 0);
        // "x" chosen on another page
        model.setSelectedItems(List.of("x", "a"));

        model.selectNextAfterRemove(List.of("a"));
        model.settle(List.of("a", "b"), 0, true);

        assertThat(model.getSelectedItems()).containsExactlyInAnyOrder("a", "x");
    }

    @Test
    void aNewKeyProviderCancelsAPendingChoice() {
        final MultiSelectionModelImpl<String> model = onPage(List.of("a", "b"), 0);
        model.setSelected("a");
        model.selectNextAfterRemove(List.of("a"));

        model.setKeyProvider(row -> row);

        assertThat(model.isRemovalPending()).isFalse();
    }

    // A model whose rows are shown on a page starting at an absolute index
    private static MultiSelectionModelImpl<String> onPage(final List<String> page, final int pageStart) {
        final MultiSelectionModelImpl<String> model = new MultiSelectionModelImpl<>();
        model.setRowIndex(row -> page.contains(row)
                ? pageStart + page.indexOf(row)
                : -1);
        return model;
    }

    // The selection types of the MultiSelectEvents the model fires from now on
    private static List<SelectionType> listenToRows(final MultiSelectionModelImpl<Row> model) {
        final List<SelectionType> fired = new ArrayList<>();
        model.addSelectionHandler(event -> fired.add(event.getSelectionType()));
        return fired;
    }

    // The selection types of the MultiSelectEvents the model fires from now on
    private static List<SelectionType> listen(final MultiSelectionModelImpl<String> model) {
        final List<SelectionType> fired = new ArrayList<>();
        model.addSelectionHandler(event -> fired.add(event.getSelectionType()));
        return fired;
    }

    private static Selection<String> selectionOf(final String... items) {
        final Selection<String> selection = new Selection<>();
        for (final String item : items) {
            selection.setSelected(item, true);
        }
        return selection;
    }

    private record Row(int id, String value) {

    }
}
