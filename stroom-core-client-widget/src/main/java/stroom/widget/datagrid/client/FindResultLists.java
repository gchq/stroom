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

package stroom.widget.datagrid.client;

import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.cellview.client.Column;

import java.util.function.Consumer;

/// The lists of results the find dialogs show (documents found, recent items, content found,
/// annotations, AI chats): a striped list (`listbox`) of a page of results, drawn by one cell, the
/// result with the focus chosen. Enter, or a double click, on a result opens it; Up on the first
/// result goes back to the dialog's filter; Left and Right show the page before or after; typing
/// goes to the next result starting with what is typed.
public final class FindResultLists {

    /// How many results a page has.
    public static final int PAGE_SIZE = 100;

    private FindResultLists() {
        // Static utility
    }

    /// Makes a list of results.
    ///
    /// @param eventBus       The application event bus.
    /// @param name           The list's name, for assistive technology, e.g. "Documents found".
    /// @param cell           Draws a result.
    /// @param selectionModel The selection model: the result with the focus is chosen.
    /// @param onOpen         Opens a result (Enter, or a double click, on it).
    /// @param onUpFromFirst  What Up on the first result does, e.g. focusing the filter, or null.
    /// @param <T>            The type of a result.
    /// @return The list.
    public static <T> DataGridWidget<T> create(final HasHandlers eventBus,
                                               final String name,
                                               final Cell<T> cell,
                                               final MultiSelectionModelImpl<T> selectionModel,
                                               final Consumer<T> onOpen,
                                               final Runnable onUpFromFirst) {
        final DataGridWidget<T> list = new DataGridWidget<>(eventBus, PAGE_SIZE);
        list.setProfile(DataGridProfile.LISTBOX);
        list.setAccessibleName(name);
        list.setStriped(true);
        list.addColumn(new Column<T, T>(cell) {
            @Override
            public T getValue(final T result) {
                return result;
            }
        }, "", 200);
        final DataGridWidgetSelectionEventManager<T> selectionEventManager =
                new DataGridWidgetSelectionEventManager<>(list, selectionModel, false);
        selectionEventManager.setMoveUpFromFirst(onUpFromFirst);
        selectionEventManager.setLeftRightPage(true);
        list.setSelectionModel(selectionModel, selectionEventManager);
        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect() && selectionModel.getSelected() != null) {
                onOpen.accept(selectionModel.getSelected());
            }
        });
        return list;
    }
}
