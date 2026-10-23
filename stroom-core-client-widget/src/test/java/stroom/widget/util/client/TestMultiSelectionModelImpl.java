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
}
