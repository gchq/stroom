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

package stroom.preferences.client.component;

import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyBinding.Category;
import stroom.widget.util.client.KeyShortcut;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// A dialog, for one use, listing the keyboard shortcuts as the user has them, for reference
/// ([Action#SHOW_KEY_BINDS], `?` by default): each action with its shortcuts and its group. The
/// shortcuts are changed in the user preferences (`KeyBindingPreferencesController`), not here.
public class KeyboardShortcutsController extends Controller {

    /// The group of actions whose shortcuts are built in, which the user can't change.
    static final String BUILT_IN = "Built in";

    private static final String CAPTION = "Keyboard Shortcuts";
    // The key code some browsers give Enter, as KeyBinding binds it
    private static final int LINE_FEED = '\n';

    private final KeyboardShortcuts view;
    private String filter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public KeyboardShortcutsController(final EventBus eventBus,
                                       final KeyboardShortcuts view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnFilterChange(value -> {
            filter = value;
            refresh();
        });
    }

    /// Shows the dialog.
    ///
    /// @param opener What shows it, which owns it until it is hidden.
    public void show(final HasControllerNode opener) {
        refresh();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(600, 560))
                .caption(CAPTION)
                .onShow(e -> view.focus())
                .fire();
    }

    private void refresh() {
        view.setRows(getRows(filter));
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
