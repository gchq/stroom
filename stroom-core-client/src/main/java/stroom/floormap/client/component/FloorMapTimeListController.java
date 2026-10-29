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

import stroom.editor.client.ChangeCurrentPreferencesEvent;
import stroom.floormap.client.FloorMapEditorHelp;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.TemporalEntry;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// The floor map editor's Time List panel ([FloorMapTimeList]): its owner gives it a fact's versions
/// and what to select, and is told of the selection and the actions. Times are shown in the user's
/// date and time preference, and drawn again when it changes; its help button shows the help.
public class FloorMapTimeListController extends Controller {

    private final DateTimeFormatter dateTimeFormatter;
    private FloorMapTimeList view;

    /// Made by GIN; [#attach(FloorMapTimeList)] gives it its list.
    ///
    /// @param eventBus          The application's event bus, which says when preferences change.
    /// @param dateTimeFormatter Shows the times as the user prefers.
    @Inject
    public FloorMapTimeListController(final EventBus eventBus,
                                      final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /// Attaches this to a Time List, e.g. one a template declares. The list is then this
    /// controller's widget.
    ///
    /// @param view The Time List.
    /// @return This, for its owner to keep.
    public FloorMapTimeListController attach(final FloorMapTimeList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        // The platform's formatter, as every grid uses: the user's time zone and pattern
        view.setTimeFormatter(dateTimeFormatter::format);
        listen(ChangeCurrentPreferencesEvent.getType(), e -> view.redraw());
        FloorMapEditorHelp.wire(view.getHelpButton(), "Time List", FloorMapEditorHelp.timeList());
        return this;
    }

    /// Shows the entries in place of those shown, with none selected.
    ///
    /// @param entries The entries, by effective time, earliest first; null for none.
    public void setEntries(final List<TemporalEntry> entries) {
        view.setEntries(entries);
    }

    /// Selects the entry active at a time, or none if every entry is later.
    ///
    /// @param timeMs The time.
    public void selectAtTime(final long timeMs) {
        view.selectAtTime(timeMs);
    }

    /// Selects the entry at a row, or the nearest row there is.
    ///
    /// @param index The row, from 0.
    public void selectAtIndex(final int index) {
        view.selectAtIndex(index);
    }

    /// @param deletedTest Whether a version ends its fact; null for none.
    public void setDeletedTest(final Predicate<TemporalEntry> deletedTest) {
        view.setDeletedTest(deletedTest);
    }

    /// @param onSelectionChange Given the entry selected, or null for none, each time it changes.
    public void setOnSelectionChange(final Consumer<TemporalEntry> onSelectionChange) {
        view.setOnSelectionChange(onSelectionChange);
    }

    /// @param onEdit Given the selected entry when Edit is pressed.
    public void setOnEdit(final Consumer<TemporalEntry> onEdit) {
        view.setOnEdit(onEdit);
    }

    /// @param onAdd Run when Add is pressed.
    public void setOnAdd(final Runnable onAdd) {
        view.setOnAdd(onAdd);
    }

    /// @param onDelete Given the selected entry when Delete is pressed.
    public void setOnDelete(final Consumer<TemporalEntry> onDelete) {
        view.setOnDelete(onDelete);
    }
}
