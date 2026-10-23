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

package stroom.preferences.client;

import stroom.preferences.client.KeyboardShortcutsPresenter.KeyboardShortcutsView;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyBinding.Category;
import stroom.widget.util.client.KeyShortcut;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A dialog listing the keyboard shortcuts as the user has them, for reference
/// ([Action#SHOW_KEY_BINDS], `?` by default): each action with its shortcuts and its group. The
/// shortcuts are changed in the user preferences ([KeyBindingPreferencesPresenter]), not here.
public class KeyboardShortcutsPresenter extends MyPresenterWidget<KeyboardShortcutsView> {

    /// The group of actions whose shortcuts are built in, which the user can't change.
    static final String BUILT_IN = "Built in";

    private static final String CAPTION = "Keyboard Shortcuts";
    // The key code some browsers give Enter, as KeyBinding binds it
    private static final int LINE_FEED = '\n';

    private final DataGridWidget<ShortcutRow> dataGrid;
    private String filter;

    @Inject
    public KeyboardShortcutsPresenter(final EventBus eventBus,
                                      final KeyboardShortcutsView view) {
        super(eventBus, view);
        dataGrid = new DataGridWidget<>(this);
        dataGrid.setTableName(CAPTION);
        dataGrid.addDefaultSelectionModel(false);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ShortcutRow::action).build(),
                "Action",
                270);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ShortcutRow::shortcuts).build(),
                "Shortcut",
                180);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ShortcutRow::group).build(),
                "Group",
                100);
        view.setList(dataGrid);
        view.setFilterHandler(value -> {
            filter = value;
            refresh();
        });
    }

    /// Shows the dialog.
    public void show() {
        refresh();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(600, 560))
                .caption(CAPTION)
                .onShow(e -> getView().focus())
                .fire();
    }

    private void refresh() {
        final List<ShortcutRow> rows = getRows(filter);
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
    }

    /// The rows the dialog shows: every action that has a shortcut, the configurable ones in their
    /// groups (General, Lists, Go to, Create) and then the built-in ones, as the user has them.
    ///
    /// @param filter Text that an action's name, shortcuts or group must contain, ignoring case,
    ///               or null or blank for every action.
    /// @return The rows.
    static List<ShortcutRow> getRows(final String filter) {
        final List<ShortcutRow> rows = new ArrayList<>();
        for (final Category category : Category.values()) {
            for (final Action action : Action.values()) {
                if (action.getCategory() == category) {
                    addRow(rows, action, category.getDisplayName());
                }
            }
        }
        for (final Action action : Action.values()) {
            if (!action.isConfigurable()) {
                addRow(rows, action, BUILT_IN);
            }
        }
        if (NullSafe.isBlankString(filter)) {
            return rows;
        }
        final String lowerFilter = filter.trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> row.matches(lowerFilter))
                .collect(Collectors.toList());
    }

    private static void addRow(final List<ShortcutRow> rows, final Action action, final String group) {
        final List<KeyShortcut> shortcuts = KeyBinding.getShortcuts(action)
                .stream()
                .filter(shortcut -> !isOtherEnterCode(shortcut))
                .collect(Collectors.toList());
        if (!shortcuts.isEmpty()) {
            rows.add(new ShortcutRow(action.getDisplayName(),
                    shortcuts.stream()
                            .map(KeyShortcut::toString)
                            .collect(Collectors.joining(", ")),
                    group));
        }
    }

    // Whether a shortcut is bound to one of the other key codes that browsers give the Enter key
    // (Mac's, or a line feed), bound as well as Enter's own so that the key works everywhere: the
    // user knows them all as Enter, which is listed
    private static boolean isOtherEnterCode(final KeyShortcut shortcut) {
        return shortcut.getKeyStrokes()
                .stream()
                .anyMatch(keyStroke -> keyStroke.getKeyCode() == KeyCodes.KEY_MAC_ENTER
                                       || keyStroke.getKeyCode() == LINE_FEED);
    }


    // --------------------------------------------------------------------------------


    /// The dialog's view: a filter above the list of shortcuts.
    public interface KeyboardShortcutsView extends View, Focus {

        /// @param widget The list of shortcuts.
        void setList(Widget widget);

        /// @param handler Told the filter's text each time it changes.
        void setFilterHandler(Consumer<String> handler);
    }


    // --------------------------------------------------------------------------------


    /// A row of the dialog.
    ///
    /// @param action    The action's name.
    /// @param shortcuts The action's shortcuts, the main one first, e.g. `Alt+R` or `W, K`.
    /// @param group     The action's group, e.g. `General`, or [#BUILT_IN].
    record ShortcutRow(String action, String shortcuts, String group) {

        private boolean matches(final String lowerFilter) {
            return action.toLowerCase(Locale.ROOT).contains(lowerFilter)
                   || shortcuts.toLowerCase(Locale.ROOT).contains(lowerFilter)
                   || group.toLowerCase(Locale.ROOT).contains(lowerFilter);
        }
    }
}
