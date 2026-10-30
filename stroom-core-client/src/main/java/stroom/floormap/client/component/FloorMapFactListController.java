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

package stroom.floormap.client.component;

import stroom.floormap.client.FloorMapEditorHelp;
import stroom.floormap.client.model.FactListRow;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The floor map editor's Fact List panel ([FloorMapFactList]): its owner gives it the facts and
/// the selection, and is told of the selection and the actions; its help button shows the help.
public class FloorMapFactListController extends Controller {

    private FloorMapFactList view;

    /// Made by GIN; [#attach(FloorMapFactList)] gives it its list.
    ///
    /// @param eventBus The application's event bus.
    @Inject
    public FloorMapFactListController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to a Fact List, e.g. one a template declares. The list is then this
    /// controller's widget.
    ///
    /// @param view The Fact List.
    /// @return This, for its owner to keep.
    public FloorMapFactListController attach(final FloorMapFactList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        FloorMapEditorHelp.wire(view.getHelpButton(), "Fact List", FloorMapEditorHelp.factList());
        // Its actions change the facts, so they follow the document's read-only state
        watch(Contexts.READ_ONLY, view::setReadOnly);
        return this;
    }

    /// @return The grid's element id, for the canvas to point `aria-describedby` at.
    public String getGridElementId() {
        return view.getGridElementId();
    }

    /// Shows the facts in place of those shown; the selection is left as it is.
    ///
    /// @param facts The facts.
    public void setFacts(final List<FactListRow> facts) {
        view.setFacts(facts);
    }

    /// Selects the fact with this key alone, or none.
    ///
    /// @param key The key, or null for none.
    public void setSelected(final String key) {
        view.setSelectedKeys(key == null
                ? Collections.emptyList()
                : Collections.singletonList(key));
    }

    /// Selects the facts with these keys, without telling the selection's callback.
    ///
    /// @param keys The keys, or null for none.
    public void setSelectedKeys(final Collection<String> keys) {
        view.setSelectedKeys(keys);
    }

    /// @return The first fact selected, or null if none is.
    public FactListRow getSelectedFact() {
        return view.getSelectedFact();
    }

    /// @param onSelectionChange Given every fact selected, each time the user changes the selection.
    public void setOnSelectionChange(final Consumer<List<FactListRow>> onSelectionChange) {
        view.setOnSelectionChange(onSelectionChange);
    }

    /// @param onAdd Run when Add New Object is pressed.
    public void setOnAdd(final Runnable onAdd) {
        view.setOnAdd(onAdd);
    }

    /// @param onEnd Given the selected fact's key when Delete From This Time is pressed.
    public void setOnEnd(final Consumer<String> onEnd) {
        view.setOnEnd(onEnd);
    }

    /// @param onDelete Given the selected fact's key when Erase History is pressed.
    public void setOnDelete(final Consumer<String> onDelete) {
        view.setOnDelete(onDelete);
    }

    /// @param onShowAll Told whether every fact is shown (Show All on) or only those at the time.
    public void setOnShowAll(final Consumer<Boolean> onShowAll) {
        view.setOnShowAll(onShowAll);
    }
}
